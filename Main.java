package gitlet;

import java.io.File;
import java.io.IOException;

/** Driver class for Gitlet, the tiny stupid version-control system.
 *  @author Rixiao Zhang
 */
public class Main {

    /** Current Working Directory.*/
    static final File CWD = new File(".");
    /** Main metadata folder. */
    static final File MAIN_FOLDER =  Utils.join(CWD, ".gitlet");
    /** Remove Folder.*/
    static final File REMOVE_FOLDER = Utils.join(MAIN_FOLDER, "remove");
    /** Branch Folder.*/
    static final File BRANCH_FOLDER = Utils.join(MAIN_FOLDER, "branch");
    /** Head pointer file.*/
    static final File HEAD_POINTER = Utils.join(MAIN_FOLDER, "headPointer");
    /** The single branch: master.*/
    static final File BRANCH_MASTER = Utils.join(BRANCH_FOLDER, "master");
    /** Current Branch.*/
    static final File CURRENT_BRANCH = Utils.join
            (Main.MAIN_FOLDER, "currentBranch");
    /** Blob Folder.*/
    static final File BLOB_FOLDER = Utils.join(MAIN_FOLDER, "blobs");

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND> .... */
    public static void main(String... args) throws IOException {
        if (args.length == 0) {
            exitWithError("Please enter a command.");
        }

        switch (args[0]) {
            case "init":
                formatChecker(1, args);
                commandInit();
                break;
            case "add":
                checkInitialization();
                formatChecker(2, args);
                commandAdd(args[1]);
                break;
            case "commit":
                checkInitialization();
                formatChecker(2, args);
                commandCommit(args[1]);
                break;
            case "rm":
                checkInitialization();
                formatChecker(2, args);
                commandDelete(args[1]);
                break;
            case "log":
                checkInitialization();
                formatChecker(1, args);
                commandLog();
                break;
            case "global-log":
                checkInitialization();
                formatChecker(1, args);
                commandGlobalLog();
                break;
            case "find":
                checkInitialization();
                formatChecker(2, args);
                commandFind(args[1]);
                break;
            case "status":
                checkInitialization();
                formatChecker(1, args);
                commandStatus();
                break;
            case "checkout":
                checkInitialization();
                formatChecker(4, args);
                commandCheckout(args);
                break;
            case "branch":
                checkInitialization();
                formatChecker(2, args);
                commandBranch(args[1]);
                break;
            case "rm-branch":
                checkInitialization();
                formatChecker(2, args);
                commandRemoveBranch(args[1]);
                break;
            case "reset":
                checkInitialization();
                formatChecker(2, args);
                commandReset(args[1]);
                break;
            case "merge":
                checkInitialization();
                formatChecker(2, args);
                commandMerge(args[1]);
                break;
            default:
                exitWithError("No command with that name exists");
        }

    }

    /**Check the number of operands for each command.
     * @param args Program Arguments. */
    private static void formatChecker(int maxLength, String... args) {
        if (args.length > maxLength) {
            exitWithError("Incorrect operands.");
        }
    }

    /**Check Initialization.*/
    private static void checkInitialization() {
        if (!MAIN_FOLDER.exists()) {
            exitWithError("Not in an initialized Gitlet directory.");
        }
    }

    /** Print out error message and exit with 0.
     * @param message  Error Message.*/
    static void exitWithError(String message) {
        if (message != null && !message.equals("")) {
            System.out.println(message);
        }
        System.exit(0);
    }

    /**Does required filesystem operations to allow for persistence.
     * (creates any necessary folders or files)*/
    private static void setupPersistence() {
        MAIN_FOLDER.mkdir();
        Stage.STAGINGAREA_FOLDER.mkdir();
        REMOVE_FOLDER.mkdir();
        Commit.COMMIT_FOLDER.mkdir();
        BRANCH_FOLDER.mkdir();
        BLOB_FOLDER.mkdir();
    }


    /** command Init.*/
    private static void commandInit() {
        if (MAIN_FOLDER.exists()) {
            exitWithError("A Gitlet version-control system already exists "
                    + "in the current directory.");
        }
        setupPersistence();
        Commit initCommit = new Commit("initial commit");
        initCommit.saveCommit();
    }


    /** command add.
     * @param fileName add the file in the CWD given the fileName.*/
    private static void commandAdd(String fileName) {
        if(!Utils.join(CWD, fileName).exists()) {
            exitWithError("File does not exist.");
        }
        Stage.add(fileName);
    }


    /** command rm.
     * @param fileName remove the file in the CWD given the fileName.*/
    private static void commandDelete(String fileName) throws IOException {
        if (!Stage.checkStaged(fileName)
                && !Stage.trackedOrNot(fileName)) {
            exitWithError("No reason to remove the file.");
        }
        Stage.delete(fileName);
    }


    /** command commit.
     * @param message commit with given message.*/
    private static void commandCommit(String message) {
        Commit newCommit = new Commit(message);
        newCommit.saveCommit();
        Stage.clearStageArea();
        Stage.clearRemoveFolder();
    }


    /** command log.*/
    private static void commandLog() {
        Commit.log();
    }


    /** command global log.*/
    private static void commandGlobalLog() {
        Commit.globalLog();
    }


    /** command find.
     * @param message find the commits with given message.*/
    private static void commandFind(String message) {
        Commit.find(message);
    }


    /** command status.*/
    private static void commandStatus() {
        Stage.status();
    }


    /** command checkout.
     * */
    private static void commandCheckout(String... args) {
        if (args.length == 2) {
            Branch.checkoutWithBranch(args[1]);
            Stage.clearStageArea();
        } else if (args.length == 3) {
            Branch.checkoutFileOnly(args[2]);
        } else if (args.length == 4) {
            if (!args[2].equals("--")) {
                exitWithError("Incorrect operands.");
            }
            Branch.checkoutWithId(args[1], args[3]);
        }
    }


    /** command branch.
     * @param branchName create a new branch with given branch name.*/
    private static void commandBranch(String branchName) {
        Branch.branch(branchName);
    }


    /** command rm-branch.
     * @param */
    private static void commandRemoveBranch(String branch) {
        Branch.rmBranch(branch);
    }


    /** command reset.*/
    private static void commandReset(String commitID) {
        Branch.reset(commitID);
        Stage.clearStageArea();
    }


    /** command merge.*/
    private static void commandMerge(String branchName) throws IOException {
        Branch.merge(branchName);
    }
}
