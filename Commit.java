package gitlet;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Date;
import java.io.File;



/** Command commit.
 *  @author Rixiao Zhang
 */
public class Commit implements Serializable {

    /** */
    static final File COMMIT_FOLDER = Utils.join(Main.MAIN_FOLDER, "commits");

    /** Constructor without merged-in parent.
     * @param meg commit message.*/
    Commit(String meg) {
        _message = meg;
        if (!getMessage().equals("")) {
            if (_message.equals("initial commit")) {
                _timestamp = "Wed Dec 31 16:00:00 1969";
                _parent = null;
                _mergedParent = null;
                _id = Utils.sha1(getType(), getMessage(), getTimeStamp());
            } else {
                nochangeChecker();
                _timestamp = new Date().toString().replaceAll("PST ", "");
                _parent = Utils.readContentsAsString
                        (Main.HEAD_POINTER);
                setBlobsReference();
                _mergedParent = null;
            }
        } else {
            Main.exitWithError("Please enter a commit message.");
        }
    }


    /** Constructor with merged-in parent.
     * @param meg commit message.
     * @param secondParent merged-in parent.*/
    Commit(String meg, String secondParent) {
        nochangeChecker();
        _message = meg;
        _timestamp = new Date().toString().replaceAll("PST ", "");
        _parent = Utils.readContentsAsString
                (Main.HEAD_POINTER);
        setBlobsReference();
        _mergedParent = secondParent;
    }

    /** Command Log.*/
    static void log() {
        String commitFile = Utils.readContentsAsString(Main.HEAD_POINTER);
        while (commitFile != null) {
            Commit cur = fromFile(commitFile);
            print(cur);
            commitFile = cur.getParent();
        }
    }


    /** Command Global Log.*/
    static void globalLog() {
        List<String> commitFiles = allFiles(COMMIT_FOLDER);
        if (commitFiles != null) {
            for (String commitFile : commitFiles) {
                Commit cur = fromFile(commitFile);
                print(cur);
            }
        }
    }


    /** Helper function for command Log and Global Log.
     * @param cur current commit.*/
    private static void print(Commit cur) {
        System.out.println("===");
        System.out.println("commit " + cur.getId());
        if (cur.getMergedParent() != null) {
            String firstPar = cur.getParent().substring(0, 7);
            String secondPar = cur.getMergedParent().substring(0, 7);
            System.out.println("Merge: " + firstPar + " " + secondPar);
        }
        System.out.println("Date: " + cur.getTimeStamp() + " -0800");
        System.out.println(cur.getMessage());
        System.out.println();
    }


    /** Print out any commit IDs with given Commit Message.
     * @param commitMessage  _message.*/
    static void find(String commitMessage) {
        List<String> commitFiles = allFiles(COMMIT_FOLDER);
        List<String> iDs = new ArrayList<>();
        if (commitFiles != null) {
            for (String commitFile : commitFiles) {
                Commit cur = fromFile(commitFile);
                if (cur.getMessage().equals(commitMessage)) {
                    iDs.add(cur.getId());
                }
            }
        }
        if (!iDs.isEmpty()) {
            for (String id : iDs) {
                System.out.println(id);
            }
        } else {
            Main.exitWithError("Found no commit with that message.");
        }
    }


    /** Write the result of concatenating the bytes of the COMMIT
     * into a file named by its _id.*/
    void saveCommit() {
        if (getParent() != null) {
            _id = hashFunction();
        }
        Utils.writeObject(Utils.join(COMMIT_FOLDER, getId()), this);
        Utils.writeContents(Main.HEAD_POINTER, getId());

        if (getParent() != null) {
            String curBranch = Branch.getCurBranchName();
            Utils.writeContents(Utils.join(Main.BRANCH_FOLDER, curBranch), getId());
        }else {
            Utils.writeContents(Main.BRANCH_MASTER, getId());
            Utils.writeContents(Main.CURRENT_BRANCH, Branch.getDefaultBranch());
        }
    }


    /** Update blobReference from the parent commit.*/
    private void setBlobsReference() {
        Commit curCommit =  Utils.readObject
                (Utils.join(COMMIT_FOLDER, getParent()), Commit.class);
        _blobsReference = curCommit.getBlobsReference();

        List<String> fileList = allFiles(Stage.STAGINGAREA_FOLDER);
        if (fileList != null) {
            for (String fileName : fileList) {
                String hashID = Utils.readContentsAsString
                        (Utils.join(Stage.STAGINGAREA_FOLDER, fileName));
                _blobsReference.put(fileName, hashID);
            }
        }

        List<String> removeFileList = allFiles(Main.REMOVE_FOLDER);
        if (removeFileList != null) {
            for (String fileName : removeFileList) {
                _blobsReference.remove(fileName);
            }
        }
    }

    /** Return the current commit. */
    static Commit currentCommit() {
        String commitId = Utils.readContentsAsString(Main.HEAD_POINTER);
        return Commit.fromFile(commitId);
    }


    /** FILENAME is SHA1 ID.
     * @return  return the commit object given the SHA1 ID.*/
    public static Commit fromFile(String fileName) {
        File commitFile = Utils.join(COMMIT_FOLDER, fileName);
        if (!commitFile.exists()) {
            Main.exitWithError("No commit with that ID found.");
        }
        return Utils.readObject(commitFile, Commit.class);
    }



    /** Return SHA1 ID(_id) of the commit object.*/
    String hashFunction() {
        List<Object> myArr = new ArrayList<>();
        myArr.add(getType());
        myArr.add(getMessage());
        myArr.add(getTimeStamp());
        myArr.add(getParent());
        myArr.addAll(getBlobsReference().values());
        if (getMergedParent() != null) {
            myArr.add(getMergedParent());
        }
        return Utils.sha1(myArr);
    }

    /**Return SHA1 ID of the object.*/
    String getId() {
        return _id;
    }

    /**Return type of the object used for SHA1 ID.*/
    private String getType() {
        return _type;
    }


    /**Return commit message.*/
    private String getMessage() {
        return _message;
    }

    /**Return timestamp.*/
    private String getTimeStamp() {
        return _timestamp;
    }


    /**Return the first parent of the commit object. */
    String getParent() {
        return _parent;
    }

    /**Return the second parent odf the commit object. */
    String getMergedParent() {
        return _mergedParent;
    }


    /**Return the map. FileName as key and sha1 ID as value.*/
    Map<String, String> getBlobsReference() {
        return _blobsReference;
    }


    /** Return all plain files in the GIVEN FOLDER.
     * @param givenFolder csdcdsc sdvsdv*/
    private static List<String> allFiles(File givenFolder) {
        return Utils.plainFilenamesIn(givenFolder);
    }

    private static void nochangeChecker() {
        List<String> additions =
                Utils.plainFilenamesIn(Stage.STAGINGAREA_FOLDER);
        List<String> removals =
                Utils.plainFilenamesIn(Main.REMOVE_FOLDER);
        if (additions.isEmpty()
                && removals.isEmpty()) {
                Main.exitWithError("No changes added to the commit.");
        }
    }

    /**SHA1 ID.*/
    private String _id;
    /**Commit message. */
    private String _message;
    /**Type of the object used for SHA1 ID.*/
    private static String _type = "commit";
    /**Commit timestamp. */
    private String _timestamp;

    /**FileName as key and sha1 ID as value.*/
    private Map<String, String> _blobsReference = new HashMap<>();
    /**First parent of the commit object. */
    private String _parent;
    /**Second parent of the commit object. */
    private String _mergedParent = null;
}


