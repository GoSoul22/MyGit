package gitlet;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Branch {

    /** Version 1 check out.*/
    static void checkoutFileOnly(String fileName) {
        String commitId = Utils.readContentsAsString(Main.HEAD_POINTER);
        checkoutWithId(commitId, fileName);
    }


    /** Version 2 check out.*/
    static void checkoutWithId(String commitId, String fileName) {
        if (!commitId.isEmpty() &&
                checkFile(commitId, Commit.COMMIT_FOLDER)){
            Commit curCommit = fromFile(commitId);
            Map<String, String> blobReference =
                    curCommit.getBlobsReference();
            if (blobReference != null
                    && !blobReference.isEmpty()
                    && blobReference.containsKey(fileName)){
                File restoringFile = Utils.join(Main.CWD, fileName);
                String blobID = blobReference.get(fileName);
                File blobFile = Utils.join(Main.BLOB_FOLDER, blobID);
                Utils.writeContents(restoringFile,
                        (Object) Utils.readContents(blobFile));
            } else {
                Main.exitWithError("File does not exist in that commit.");
            }
        } else {
            Main.exitWithError("No commit with that id exists.");
        }
    }


    /** Version 3 check out.*/
    static void checkoutWithBranch(String branchName) {
        if (checkFile(branchName, Main.BRANCH_FOLDER)) {
            if (!inCurrentBranch(branchName)) {
                File headOfTheBranch = Utils.join(Main.BRANCH_FOLDER, branchName);
                String branchId = Utils.readContentsAsString(headOfTheBranch);
                String headId = Utils.readContentsAsString(Main.HEAD_POINTER);
                List<String> workingFiles = Utils.plainFilenamesIn(Main.CWD);
                Commit commitBranch = fromFile(branchId);
                Map<String, String> branchBlobReference = commitBranch.getBlobsReference();
                untrackedAndOverwritten(workingFiles, branchBlobReference);
                for (String fileName : branchBlobReference.keySet()){
                    String blobID = branchBlobReference.get(fileName);
                    File blobFile = Utils.join(Main.BLOB_FOLDER, blobID);
                    File newFile = Utils.join(Main.CWD, fileName);
                    byte[] content = Utils.readContents(blobFile);
                    Utils.writeContents(newFile, (Object) content);
                }
                Commit curCommit = fromFile(headId);
                Map<String, String> curBlobReference = curCommit.getBlobsReference();
                for (String fileName : curBlobReference.keySet()) {
                    if (!branchBlobReference.containsKey(fileName)) {
                        Utils.join(Main.CWD, fileName).delete();
                    }
                }
                Utils.writeContents
                        (Main.HEAD_POINTER, branchId);
                Utils.writeContents
                        (Main.CURRENT_BRANCH, branchName);
            } else {
                Main.exitWithError("No need to checkout the current branch.");
            }
        } else {
            Main.exitWithError("No such branch exists.");
        }
    }


    /** Create a new branch.*/
    static void branch(String branchName) {
        if (!checkFile(branchName, Main.BRANCH_FOLDER)){
            File newBranch = Utils.join
                    (Main.BRANCH_FOLDER, branchName);
            Utils.writeContents(newBranch,
                    Utils.readContentsAsString
                            (Main.HEAD_POINTER));
        } else {
            Main.exitWithError("A branch with that name already exists.");
        }
    }


    /** Remove the GIVEN BRANCH.*/
    static void rmBranch(String givenBranch) {
        if (inCurrentBranch(givenBranch)) {
            Main.exitWithError("Cannot remove the current branch.");
        } else if (!checkFile(givenBranch, Main.BRANCH_FOLDER)) {
            Main.exitWithError("A branch with that name does not exist.");
        } else {
            Utils.join(Main.BRANCH_FOLDER, givenBranch).delete();
        }
    }


    /** Checks out all the files tracked by the given commit.
     * Removes tracked files that are not present in that commit.
     * Also moves the current branch's head to that commit node.
     * Assume the corresponding givenCommit is in the current branch.  */
    static void reset(String givenCommitID) {
        if (checkFile(givenCommitID, Commit.COMMIT_FOLDER)) {
            Commit givenCommit = Utils.readObject
                    (Utils.join(Commit.COMMIT_FOLDER, givenCommitID), Commit.class);
            Map<String, String> blobReference = givenCommit.getBlobsReference();
            List<String> workingFiles = allFiles(Main.CWD);
            untrackedAndOverwritten(workingFiles, blobReference);

            for (String file : blobReference.keySet()) {
                checkoutWithId(givenCommitID, file);
            }

            String curCommitID = Utils.readContentsAsString(Main.HEAD_POINTER);
            Commit curCommit = Utils.readObject
                    (Utils.join(Commit.COMMIT_FOLDER, curCommitID), Commit.class);
            Map<String, String> curblobReference = curCommit.getBlobsReference();
            for (String file : curblobReference.keySet()) {
                if (!blobReference.containsKey(file)) {
                    Utils.join(Main.CWD, file).delete();
                }
            }

            String curBranch = getCurBranchName();
            Utils.writeContents
                    (Utils.join(Main.BRANCH_FOLDER, curBranch), givenCommitID);
            Utils.writeContents
                    (Utils.join(Main.HEAD_POINTER), givenCommitID);
        } else {
            Main.exitWithError("No commit with that id exists.");
        }
    }


    static void merge(String givenBranch) throws IOException {
        mergeChecker(givenBranch);
        Commit curCommit = Commit.currentCommit();
        String curCommitID = curCommit.getId();
        String givenCommitID = Utils.readContentsAsString
                (Utils.join(Main.BRANCH_FOLDER, givenBranch));
        Commit givenCommit = Utils.readObject
                (Utils.join(Commit.COMMIT_FOLDER, givenCommitID), Commit.class);
        Commit splitPoint = findLCA(curCommit, givenCommit);
        assert (splitPoint != null);
        if(splitPoint.getId().equals(givenCommitID)) {
            Main.exitWithError("Given branch is an ancestor of the current branch.");
        } else if (splitPoint.getId().equals(curCommitID)) {
            checkoutWithBranch(givenBranch);
            Main.exitWithError("Current branch fast-forwarded.");
        }

        Map<String, List<String>> modificationGiven =
                modification(givenCommit, splitPoint);
        List<String> modifiedGiven = null;
        if (modificationGiven != null
                && !modificationGiven.isEmpty() ) {
            modifiedGiven = modificationGiven.get("modified");

        }

        Map<String, List<String>> modificationCur =
                modification(curCommit, splitPoint);
        List<String> modifiedCurrent = null;
        List<String> unmodifiedCurrent = null;
        if (modificationCur != null
                && !modificationCur.isEmpty()) {
            if (modificationCur.get("modified") != null
                    && !modificationCur.get("modified").isEmpty()) {
                modifiedCurrent = modificationCur.get("modified");
            }
            if (modificationCur.get("unmodified") != null
                    && !modificationCur.get("unmodified").isEmpty()) {
                unmodifiedCurrent = modificationCur.get("unmodified");
            }
        }

        List<String> file1 = commonFiles(modifiedGiven, unmodifiedCurrent);
        if (file1 != null
                && !file1.isEmpty()) {
            for (String file : file1) {
                checkoutWithId(givenCommitID, file);
                Stage.add(file);
            }
        }

        Map<String, List<String>> preGiven = presenting(givenCommit, splitPoint);
        List<String> absentGiven = preGiven.get("absent");
        List<String> file5 = onlyPresent(absentGiven, curCommit);
        if (file5 != null
                && !file5.isEmpty() ) {
            for (String file : file5) {
                checkoutWithId(givenCommitID, file);
                Stage.add(file);
            }
        }

        List<String> file6 = onlyPresent(unmodifiedCurrent, givenCommit);
        if (file6 != null
                && !file6.isEmpty()) {
            for (String file : file6) {
                Stage.delete(file);
            }
        }

        List<String> bothPresendAndModified =
                commonFiles(modifiedGiven, modifiedCurrent);

        Map<String, String> givenMap =
                givenCommit.getBlobsReference();
        Map<String, String> curMap =
                curCommit.getBlobsReference();

        List<String> file8 = new ArrayList<>();
        List<String> firstCase = diffContent
                    (givenMap, curMap, bothPresendAndModified);

        List<String>  secondCase1 = onlyPresent(modifiedCurrent, givenCommit);
        List<String>  secondCase2 = onlyPresent(modifiedGiven, curCommit);

        List<String> thirdCase = diffContent(givenMap, curMap, onlyPresent
                                (commonFiles(givenCommit, curCommit), splitPoint));

        if (firstCase != null) {
            file8.addAll(firstCase);
        }
        if (secondCase1 != null) {
            file8.addAll(secondCase1);
        }
        if (secondCase2 != null) {
            file8.addAll(secondCase2);
        }
        if (thirdCase != null) {
            file8.addAll(thirdCase);
        }
        if (!file8.isEmpty()) {
            for (String file : file8) {
                overwriteContent(file, givenMap.get(file),
                        curMap.get(file));
                Stage.add(file);
            }
            System.out.println("Encountered a merge conflict.");
        }

        String message;
        String cBranchName = getCurBranchName();
        message = "Merged " + givenBranch
                + " into " + cBranchName + ".";
        Commit autoCommit = new Commit(message, givenCommitID);
        autoCommit.saveCommit();
        Stage.clearStageArea();
        Stage.clearRemoveFolder();
    }

    static void mergeChecker(String givenBranchName) {
        List<String> additions = allFiles(Stage.STAGINGAREA_FOLDER);
        List<String> removals = allFiles(Main.REMOVE_FOLDER);
        if (!additions.isEmpty()
                || !removals.isEmpty()) {
            Main.exitWithError("You have uncommitted changes.");
        }

        if (!checkFile(givenBranchName, Main.BRANCH_FOLDER)) {
            Main.exitWithError("A branch with that name does not exist. ");
        }

        String curBranch = getCurBranchName();
        if (curBranch.equals(givenBranchName)) {
            Main.exitWithError("Cannot merge a branch with itself.");
        }

        List<String> workingFiles = Utils.plainFilenamesIn(Main.CWD);
        Commit givenCommit = fromFile(Utils.readContentsAsString
                (Utils.join(Main.BRANCH_FOLDER, givenBranchName)));
        untrackedAndOverwritten(workingFiles,
                givenCommit.getBlobsReference());
    }


    /** Return the latest common ancestor of the current and given branch heads.
     * Implemented by BFS Algorithm. */
    static Commit findLCA(Commit curCommit,
                          Commit givenCommit){

        HashSet<String> givenAncestors = ancestors(givenCommit);
        Queue<String> visiting = new LinkedList<>();
        visiting.add(curCommit.getId());
        while (!visiting.isEmpty()) {
            String curCommitID = visiting.peek();
            Commit temp = fromFile(curCommitID);
            if (givenAncestors.contains
                    (curCommitID)) {
               return temp;
            }
            String parent = temp.getParent();
            String mergedParent = temp.getMergedParent();
            if (parent != null
                    && !visiting.contains(parent)) {
                visiting.add(parent);
            }

            if (mergedParent != null
                    && !visiting.contains(mergedParent)) {
                visiting.add(mergedParent);
            }
            visiting.poll();
        }
        return null;
    }


    static HashSet<String> ancestors(Commit givenCommit) {
        HashSet<String> result = new HashSet<>();
        Queue<String> visiting = new LinkedList<>();
        visiting.add(givenCommit.getId());
        result.add(givenCommit.getId());
        if (givenCommit.getMergedParent() != null) {
            visiting.add(givenCommit.getMergedParent());
            result.add(givenCommit.getMergedParent());
        }

        while (!visiting.isEmpty()) {
            String curCommitID = visiting.peek();
            Commit curCommit = fromFile(curCommitID);
            String parent = curCommit.getParent();
            String mergedParent = curCommit.getMergedParent();
            if (parent != null
                    && !result.contains(parent)) {
                visiting.add(parent);
                result.add(parent);
            }

            if (mergedParent != null
                    && !result.contains(mergedParent)) {
                visiting.add(mergedParent);
                result.add(mergedParent);
            }
            visiting.poll();
        }
        return result;
    }


    /** Return two lists of files.
     * The first list contains all the files that are present and modified
     * in the GIVEN COMMIT since the SPLIT POINT.
     * The second list contains all the files that are present but NOT modified
     * in the GIVEN COMMIT since the SPLIT POINT. */
    static Map<String, List<String>> modification(
            Commit givenCommit, Commit splitPoint){
        Map<String, String> givenMap =
                givenCommit.getBlobsReference();
        Map<String, String> splitMap =
                splitPoint.getBlobsReference();
        List<String> commandFiles =
                commonFiles(givenCommit, splitPoint);
        List<String> modified = new ArrayList<>();
        List<String> unmodified = new ArrayList<>();
        if (commandFiles != null
                && !commandFiles.isEmpty()) {
            for (String file : commandFiles){
                String givenHashID = givenMap.get(file);
                String splitHashID = splitMap.get(file);
                if (givenHashID.equals(splitHashID)){
                    unmodified.add(file);
                } else{
                    modified.add(file);
                }
            }
            HashMap<String, List<String>> result =
                    new HashMap<>();
            result.put("modified", modified);
            result.put("unmodified" , unmodified);
            return result;
        }
        return null;
    }


    /** Return two lists of file.
     * The first list contains all the files that are present
     * in the GIVEN COMMIT since the SPLIT POINT.
     * The second list contains all the files that are NOT present
     * in the GIVEN COMMIT since the SPLIT POINT. */
    static Map<String, List<String>> presenting(Commit givenCommit,
                                                Commit splitPoint){
        Map<String, String> givenMap =
                givenCommit.getBlobsReference();
        Map<String, String> splitMap =
                splitPoint.getBlobsReference();
        List<String> present = new ArrayList<>();
        List<String> absent = new ArrayList<>();
        for (String file : givenMap.keySet()){
            if (splitMap.containsKey(file)) {
                present.add(file);
            } else {
                absent.add(file);
            }
        }
        HashMap<String, List<String>> result =
                new HashMap<>();
        result.put("present", present);
        result.put("absent", absent);
        return result;
    }


    /** Return all the files that are shared by the two given commits.*/
    static List<String> commonFiles(Commit givenCommit,
                                    Commit anotherCommit) {
        Map<String, String> givenMap =
                givenCommit.getBlobsReference();
        Map<String, String> anotherMap =
                anotherCommit.getBlobsReference();
        List<String> result = new ArrayList<>();
        for (String file : givenMap.keySet()){
            if (anotherMap.containsKey(file)){
                result.add(file);
            }
        }
        return result.isEmpty()? null : result;
    }


    /** Return all the files that are shared by the two given List of files.*/
    static List<String> commonFiles(List<String> files,
                                    List<String> otherFiles) {
        if (files != null
                && otherFiles != null) {
            List<String> result = new ArrayList<>();
            for (String file : files){
                if (otherFiles.contains(file)) {
                    result.add(file);
                }
            }
            return result;
        }
        return null;
    }


    /** Return all the files in FILES that are NOT present in the given COMMIT.*/
    static List<String> onlyPresent(List<String> files,  Commit givenCommit){
        if (files != null) {
            List<String> result = new ArrayList<>();
            Map<String, String> givenMap =
                    givenCommit.getBlobsReference();
            for (String file : files) {
                if (!givenMap.containsKey(file)){
                    result.add(file);
                }
            }
            return result.isEmpty()? null : result;
        }
        return null;
    }


    /** Return all the files in FILES that have different content in both commits.*/
    static List<String> diffContent(Map<String, String> cur,
                                   Map<String, String> given,
                                   List<String> files) {
        if (files != null
                && !files.isEmpty() ) {
            List<String> result = new ArrayList<>();
                for (String file : files){
                    String givenFileID = given.get(file);
                    String curFileID = cur.get(file);
                    if(!givenFileID.equals(curFileID)) {
                        result.add(file);
                    }
                }
            return result.isEmpty()? null : result;
        }
        return null;
    }



    static void overwriteContent
            (String fileName, String givenBlobID, String curBlobID) {

        byte[] givenContent = null;
        byte[] curContent = null;
        if (givenBlobID != null) {
             givenContent = Utils.readContents
                    (Utils.join(Main.BLOB_FOLDER, givenBlobID));
        }
        if (curBlobID != null) {
            curContent = Utils.readContents
                    (Utils.join(Main.BLOB_FOLDER, curBlobID));
        }

        String newline = System.lineSeparator();
        String head = "<<<<<<< HEAD" + newline;
        String separator = "=======" + newline;
        String end = ">>>>>>>" + newline + "";

        if (givenBlobID == null) {
            Utils.writeContents(Utils.join(Main.CWD, fileName),
                    head, curContent, separator, end);
        } else if (curBlobID == null) {
            Utils.writeContents(Utils.join(Main.CWD, fileName),
                    head, separator, givenContent, end);
        } else {
            Utils.writeContents(Utils.join(Main.CWD, fileName),
                    head, curContent, separator, givenContent, end);
        }
    }


    /** Return all the plain files in the GIVEN FOLDER.*/
    private static List<String> allFiles(File givenFolder) {
        return Utils.plainFilenamesIn
                (givenFolder);
    }


    /** FILENAME is SHA1 ID.*/
    private static Commit fromFile(String fileName) {
        List<String> files = allFiles(Commit.COMMIT_FOLDER);
        for (String file : files) {
            if (file.substring(0, fileName.length())
                    .equals(fileName)) {
                fileName = file;
                break;
            }
        }
        File commitFile =
                Utils.join(Commit.COMMIT_FOLDER, fileName);
        if (!commitFile.exists()) {
            Main.exitWithError("No commit with that ID found.");
        }
        return Utils.readObject
                (commitFile, Commit.class);
    }


    /** Return true iff the FILE exists in the GIVEN FOLDER*/
    static boolean checkFile(String givenFile, File givenFolder) {
        List<String> Files = allFiles(givenFolder);
        if (Files != null
                && !Files.isEmpty()) {
            for (String fileName : Files) {
                if ((fileName.length()
                        >= givenFile.length())
                    && (fileName.substring(0, givenFile.length()).
                        equals(givenFile))) {
                    return true;
                }
            }
        }
        return false;
    }


    /** Return true iff the GIVEN BRANCH is the current branch.*/
    private static boolean inCurrentBranch(String givenBranch){
        String currentBranch = getCurBranchName();
        return currentBranch.equals
                (givenBranch);
    }


    /** Return true iff FILE is tracked by current branch.*/
    private static boolean trackedInCurBranch(String fileName){
        Commit curCommit = Commit.currentCommit();
        Map<String, String> blobReference =
                curCommit.getBlobsReference();
        return blobReference.containsKey
                (fileName);
    }


    /** Exit with error iff there is at least one file
     * which is untracked and could be overwritten.*/
    private static void untrackedAndOverwritten(List<String> workingFiles,
                                                Map<String, String> blobReference) {
        if (workingFiles != null
                && !workingFiles.isEmpty()) {
            for (String fileName : workingFiles) {
                if (!trackedInCurBranch(fileName)
                        && blobReference.containsKey(fileName)) {
                    Main.exitWithError("There is an untracked file in the way " +
                            "delete it or add it first.");
                }
            }
        }
    }

    /** Return the current branch name.*/
    public static String getCurBranchName(){
        return Utils.readContentsAsString
                (Main.CURRENT_BRANCH);
    }


    /** Return the default branch 'master'.*/
    public static String getDefaultBranch(){
        return _defaultBranch;
    }


    /** Default Branch.*/
    private static final String _defaultBranch  = "master";
}


