package gitlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.io.File;


/** Manipulate Stage Area.
 *  @author Rixiao Zhang
 */
public class Stage {

    /** Folder for copied files.*/
     static final File STAGINGAREA_FOLDER =
            Utils.join(Main.MAIN_FOLDER, "stageArea");


    /**Adds a copy of the file as it currently exists to the staging area. */
    static void add(String fileName) {
        File theFile = Utils.join(Main.CWD, fileName);
        String hashID = hashFuction(theFile);

        if (checkCurrentCommit(fileName, hashID)) {
            Utils.join(STAGINGAREA_FOLDER, fileName).delete();
        } else {
            Utils.writeContents(Utils.join(STAGINGAREA_FOLDER, fileName), hashID);
            File blobFile = Utils.join(Main.BLOB_FOLDER, hashID);
            byte[] content = Utils.readContents(theFile);
            Utils.writeContents(blobFile, (Object) content);
        }

        List<String> files = allFiles(Main.REMOVE_FOLDER);
        if (files != null
                &&!files.isEmpty()
                && files.contains(fileName)) {
            Utils.join(Main.REMOVE_FOLDER, fileName).delete();
        }
    }


    /** Unstage the file if it is currently staged. */
    static void delete(String fileName) throws IOException {
        Map<String, String> blobsReference = Commit.currentCommit().getBlobsReference();
        if (blobsReference != null
                && blobsReference.containsKey(fileName)) {
            File removeFile = Utils.join(Main.REMOVE_FOLDER, fileName);
            removeFile.createNewFile();
            Utils.join(Main.CWD, fileName).delete();
        }
        Utils.join(STAGINGAREA_FOLDER, fileName).delete();
    }


    /** Return true iff the current working version of the file is identical
     * to the version in the current(parent) commit.*/
    static boolean checkCurrentCommit(String fileName, String hashID) {
         Map<String, String> blobs = Commit.currentCommit().getBlobsReference();
         if (blobs != null && blobs.containsKey(fileName)) {
             return blobs.get(fileName).equals(hashID);
         }
         return false;
    }


    /** Return true iff the file is tracked by the current commit.*/
    static boolean trackedOrNot(String fileName) {
        Map<String, String> blobs = Commit.currentCommit().getBlobsReference();
        if (blobs!= null) {
            return blobs.containsKey(fileName);
        }
        return false;
    }


    static boolean checkStaged(String fileName) {
        List<String> file = allFiles(STAGINGAREA_FOLDER);
        if (file != null) {
            return file.contains(fileName);
        }
        return false;
    }


    /** The content and type are hashed*/
    static String hashFuction(File givenFile) {
        List<Object> values = new ArrayList<>();
        values.add("blob");
        values.add(Utils.readContents(givenFile));
        return Utils.sha1(values);
    }


    static void clearStageArea() {
        List<String> files = allFiles(STAGINGAREA_FOLDER);
        if (files != null) {
            for(String file : files) {
               Utils.join(STAGINGAREA_FOLDER, file).delete();
            }
        }
    }


    static void clearRemoveFolder() {
        List<String> files = allFiles(Main.REMOVE_FOLDER);
        if (files != null) {
            for(String file : files) {
                Utils.join(Main.REMOVE_FOLDER, file).delete();
            }
        }
    }


    static public void printFileName(File folder) {
        List<String> plainFiles = allFiles(folder);
        if (plainFiles != null && !plainFiles.isEmpty()) {
            if (folder ==  Main.BRANCH_FOLDER) {
                String currentBranch = Utils.readContentsAsString(Main.CURRENT_BRANCH);
                for (int i = 0; i < plainFiles.size(); i++) {
                    if (plainFiles.get(i).equals
                            (currentBranch)) {
                        plainFiles.set(i, "*" + plainFiles.get(i));
                        break;
                    }
                }
            }
            for (String fileName : plainFiles) {
                System.out.println(fileName);
            }
        }
    }


    static public List<String> allFiles(File givenFolder) {
        return Utils.plainFilenamesIn(givenFolder);
    }


    static void status() {
        System.out.println("=== Branches ===");
        printFileName(Main.BRANCH_FOLDER);
        System.out.println();

        System.out.println("=== Staged Files ===");
        printFileName(STAGINGAREA_FOLDER);
        System.out.println();

        System.out.println("=== Removed Files ===");
        printFileName(Main.REMOVE_FOLDER);
        System.out.println();

        System.out.println("=== Modifications Not Staged For Commit ===");
        System.out.println();

        System.out.println("=== Untracked Files ===");
        System.out.println();
    }


}
