package fcu.selab.progedu.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import fcu.selab.progedu.data.AssignmentUser;
import fcu.selab.progedu.data.PairMatching;
import fcu.selab.progedu.data.ReviewSetting;
import fcu.selab.progedu.db.PairMatchingDbManager;
import fcu.selab.progedu.db.ReviewSettingDbManager;
import fcu.selab.progedu.db.ReviewSettingMetricsDbManager;
import org.jsoup.nodes.Document;
import org.gitlab.api.models.GitlabProject;
import org.gitlab.api.models.GitlabUser;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fcu.selab.progedu.config.CourseConfig;
import fcu.selab.progedu.config.GitlabConfig;
import fcu.selab.progedu.config.JenkinsConfig;
import fcu.selab.progedu.conn.GitlabService;
import fcu.selab.progedu.conn.JenkinsService;
import fcu.selab.progedu.conn.TomcatService;
import fcu.selab.progedu.data.Assignment;
import fcu.selab.progedu.data.User;
import fcu.selab.progedu.db.AssignmentDbManager;
import fcu.selab.progedu.db.AssignmentUserDbManager;
import fcu.selab.progedu.db.CommitRecordDbManager;
import fcu.selab.progedu.db.ScreenshotRecordDbManager;
import fcu.selab.progedu.db.UserDbManager;
import fcu.selab.progedu.exception.LoadConfigFailureException;
import fcu.selab.progedu.project.AssignmentFactory;
import fcu.selab.progedu.project.AssignmentType;
import fcu.selab.progedu.project.ProjectTypeEnum;
import fcu.selab.progedu.utils.ExceptionUtil;
import fcu.selab.progedu.utils.Linux;
import fcu.selab.progedu.utils.ZipHandler;

@Path("assignment/")
public class AssignmentService {
  private static AssignmentService instance = new AssignmentService();

  public static AssignmentService getInstance() {
    return instance;
  }

  private GitlabService gitlabService = GitlabService.getInstance();
  private GitlabUser root = gitlabService.getRoot();
  private ZipHandler zipHandler;
  private JenkinsService jenkins = JenkinsService.getInstance();
  private TomcatService tomcatService = TomcatService.getInstance();
  private GitlabConfig gitlabData = GitlabConfig.getInstance();
  private JenkinsConfig jenkinsData = JenkinsConfig.getInstance();
  private CourseConfig courseConfig = CourseConfig.getInstance();
  private UserService userService = UserService.getInstance();
  private String mailUsername;
  private String mailPassword;
  private String gitlabRootUsername;
  private AssignmentDbManager dbManager = AssignmentDbManager.getInstance();
  private AssignmentUserDbManager auDbManager = AssignmentUserDbManager.getInstance();
  private UserDbManager userDbManager = UserDbManager.getInstance();
  private CommitRecordDbManager crDbManager = CommitRecordDbManager.getInstance();
  private ScreenshotRecordDbManager srDbManager = ScreenshotRecordDbManager.getInstance();
  private ReviewSettingDbManager rsDbManager = ReviewSettingDbManager.getInstance();
  private PairMatchingDbManager pmDbManager = PairMatchingDbManager.getInstance();
  private ReviewSettingMetricsDbManager rsmDbManager = ReviewSettingMetricsDbManager.getInstance();
  private final String tempDir = System.getProperty("java.io.tmpdir");
  private final String uploadDir = tempDir + "/uploads/";
  private final String testDir = tempDir + "/tests/";
  private final String projectDir = System.getProperty("catalina.base");
  private final String imageTempName = "/temp_images/";
  private final String imageTempDir = projectDir + imageTempName;
  private static final Logger LOGGER = LoggerFactory.getLogger(AssignmentService.class);

  boolean isSave = true;

  private long testZipChecksum = 0;
  private String testZipUrl = "";

  /**
   * Constuctor
   */
  public AssignmentService() {
    try {
      zipHandler = new ZipHandler();
      mailUsername = jenkinsData.getMailUser();
      mailPassword = jenkinsData.getMailPassword();
      gitlabRootUsername = gitlabData.getGitlabRootUsername();
    } catch (LoadConfigFailureException e) {
      LOGGER.debug(ExceptionUtil.getErrorInfoFromException(e));
      LOGGER.error(e.getMessage());
    }
  }

  /**
   * @param assignmentName assignment name
   * @param releaseTime    release time
   * @param readMe         read me
   * @param assignmentType assignment type
   * @param file           file
   * @param fileDetail     file detail
   * @return abc
   * @throws Exception abc
   */
  @POST
  @Path("create")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Produces(MediaType.APPLICATION_JSON)
  public Response createAssignment(
      @FormDataParam("assignmentName") String assignmentName,
      @FormDataParam("releaseTime") Date releaseTime, @FormDataParam("deadline") Date deadline,
      @FormDataParam("readMe") String readMe, @FormDataParam("fileRadio") String assignmentType,
      @FormDataParam("file") InputStream file,
      @FormDataParam("file") FormDataContentDisposition fileDetail) {

    final AssignmentType assignment = AssignmentFactory.getAssignmentType(assignmentType);
    final ProjectTypeEnum projectTypeEnum = ProjectTypeEnum.getProjectTypeEnum(assignmentType);
    // 1. Create root project and get project id and url
    createRootProject(assignmentName);

    // 2. Clone the project to C:\\Users\\users\\AppData\\Temp\\uploads
    final String cloneDirectoryPath = gitlabService.cloneProject(gitlabRootUsername,
        assignmentName);
//
//    // 3. Store Zip File to folder if file is not empty
    String filePath = tomcatService.storeFileToServer(file, fileDetail, assignment);

    // 4. Unzip the uploaded file to tests folder and uploads folder on tomcat,
    // extract main method from tests folder, then zip as root project
    String testDirectory = testDir + assignmentName;
    zipHandler.unzipFile(filePath, cloneDirectoryPath);
    zipHandler.unzipFile(filePath, testDirectory);
    assignment.createTemplate(cloneDirectoryPath);
    testZipChecksum = assignment.createTestCase(testDirectory).getChecksum();
    testZipUrl = assignment.createTestCase(testDirectory).getZipFileUrl();

    // 5. Add .gitkeep if folder is empty.
    tomcatService.findEmptyFolder(cloneDirectoryPath);

    // 6. Copy all description image to temp/images
    ArrayList<String> paths = findAllDescriptionImagePaths(readMe);
    for (String path : paths) {
      String targetPath = path.replace("temp_images", "images");
      tomcatService.copyFileToTarget(projectDir + path, projectDir + targetPath);
    }
    tomcatService.removeFile(imageTempDir);

    // 7. If README is not null
    // First, we need to modify images path
    try {
      readMe = readMe.replaceAll(imageTempName, courseConfig.getTomcatServerIp() + "/images/");
      if (!readMe.equals("<br>") || !"".equals(readMe) || !readMe.isEmpty()) {
        // Add readme to folder
        tomcatService.createReadmeFile(readMe, cloneDirectoryPath);
      }
    } catch (LoadConfigFailureException e) {
      LOGGER.debug(ExceptionUtil.getErrorInfoFromException(e));
      LOGGER.error(e.getMessage());
    }

    // 8. git push
    gitlabService.pushProject(cloneDirectoryPath);

    // 9. String removeTestDirectoryCommand = "rm -rf tests/" + name;
    tomcatService.removeFile(testDir + assignmentName);

    // 10. import project information to database
    boolean hasTemplate = false;

    addProject(assignmentName, releaseTime, deadline, readMe, projectTypeEnum, hasTemplate,
        testZipChecksum, testZipUrl);

    List<User> users = userService.getStudents();
    for (User user : users) {
      createAssignmentSettings(user.getUsername(), assignmentName);
    }

    // 11. remove project file in linux
    tomcatService.removeFile(uploadDir);
    return Response.ok().build();
  }

  /**
   * @param assignmentName    assignment name
   * @param releaseTime       release time
   * @param readMe            read me
   * @param assignmentType    assignment type
   * @param file              file
   * @param fileDetail        file detail
   * @param amount            amount
   * @param reviewStartTime   review release time
   * @param reviewEndTime     review deadline
   * @param metrics           metrics
   * @return response
   * @throws Exception abc
   */
  @POST
  @Path("peerReview/create")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Produces(MediaType.APPLICATION_JSON)
  public Response createPeerReview(
      @QueryParam("assignmentName") String assignmentName,
      @FormDataParam("releaseTime") Date releaseTime,
      @FormDataParam("deadline") Date deadline,
      @FormDataParam("readMe") String readMe,
      @FormDataParam("fileRadio") String assignmentType,
      @FormDataParam("file") InputStream file,
      @FormDataParam("file") FormDataContentDisposition fileDetail,
      @QueryParam("amount") int amount,
      @FormDataParam("reviewStartTime") Date reviewStartTime,
      @FormDataParam("reviewEndTime") Date reviewEndTime,
      @FormDataParam("metrics") int... metrics
  ) {
    Response response = null;

    try {
//      // 1. create assignment
//      createAssignment(assignmentName,
//          releaseTime, deadline, readMe, assignmentType, file, fileDetail);
//
//      // 2. create peer review setting
//      int assignmentId = dbManager.getAssignmentIdByName(assignmentName);
//      rsDbManager.insertReviewSetting(assignmentId, amount, reviewStartTime, reviewEndTime);
//
//      // 3. set review metrics for specific peer review
//      int reviewSettingId = rsDbManager.getReviewSettingIdByAid(assignmentId);
//      for (int metricId: metrics) {
//        rsmDbManager.insertReviewSettingMetrics(reviewSettingId, metricId);
//      }

      // 4. set random reviewer and review status for each assignment_user
//      randomPairMatching(amount, assignmentName);

      response = Response.ok().build();
    } catch (Exception e) {
      LOGGER.debug(ExceptionUtil.getErrorInfoFromException(e));
      LOGGER.error(e.getMessage());
      response = Response.serverError().entity(e.getMessage()).build();
    }
    return response;
  }

  private void createRootProject(String name) {
    gitlabService.createRootProject(name);
  }

  public List<String> getAllAssignmentNames() {
    return dbManager.getAllAssignmentNames();
  }

  private String getRootProjectUrl(String name) {
    String url = null;
    String gitlabUrl = null;
    try {
      gitlabUrl = gitlabData.getGitlabRootUrl();
    } catch (LoadConfigFailureException e) {
      LOGGER.debug(ExceptionUtil.getErrorInfoFromException(e));
      LOGGER.error(e.getMessage());
    }
    List<GitlabProject> rootProjects = gitlabService.getProject(root);
    for (GitlabProject project : rootProjects) {
      String proName = project.getName();
      if (proName.equals(name)) {
        url = gitlabUrl + "/root/" + name;
      }
    }
    return url;
  }

  /**
   * @param readme Description details
   * @return All images paths
   */
  public ArrayList<String> findAllDescriptionImagePaths(String readme) {
    ArrayList<String> paths = new ArrayList<>();
    Document doc = Jsoup.parse(readme);
    Elements srcAttributes = doc.select("img[src]");
    for (Element src : srcAttributes) {
      paths.add(src.attr("src"));
    }
    return paths;
  }

  /**
   * Send the notification email to student
   */
  public void sendEmail(String email, String name) {
    final String username = mailUsername;
    // final String password = "csclbyqwjhgogypt";// your password
    final String password = mailPassword;

    Properties props = new Properties();
    props.put("mail.smtp.host", "smtp.gmail.com");
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.starttls.enable", "true");
    props.put("mail.smtp.port", "587");
    Session session = Session.getInstance(props, new javax.mail.Authenticator() {
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(username, password);
      }
    });

    try {
      String content = "You have a new assignment \"" + name + "\" !";
      MimeMessage message = new MimeMessage(session);
      message.setFrom(new InternetAddress(username));
      message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
      message.setSubject("New assignment notification", "utf-8");
      message.setContent(content, "text/html;charset=utf-8");

      Transport.send(message);

    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Add a project to database
   *
   * @param name        Project name
   * @param deadline    Project deadline
   * @param readMe      Project readme
   * @param projectType File type
   * @param hasTemplate Has template
   */
  public void addProject(String name, Date releaseTime, Date deadline, String readMe,
                         ProjectTypeEnum projectType, boolean hasTemplate,
                         long testZipChecksum, String testZipUrl) {
    Assignment assignment = new Assignment();
    Date date = tomcatService.getCurrentTime();
    assignment.setName(name);
    assignment.setCreateTime(date);
    assignment.setReleaseTime(releaseTime);
    assignment.setDeadline(deadline);
    assignment.setDescription(readMe);
    assignment.setType(projectType);
    assignment.setHasTemplate(hasTemplate);
    assignment.setTestZipChecksum(testZipChecksum);
    assignment.setTestZipUrl(testZipUrl);

    dbManager.addAssignment(assignment);
  }

  /**
   * Add auid to database
   *
   * @param username       username
   * @param assignmentName assignment name
   */
  public void addAuid(String username, String assignmentName) {
    int aid = dbManager.getAssignmentIdByName(assignmentName);
    int uid = userDbManager.getUserIdByUsername(username);

    auDbManager.addAssignmentUser(aid, uid);
  }

  /**
   * delete projects
   *
   * @param name project name
   * @return response
   */
  @POST
  @Path("delete")
  @Produces(MediaType.APPLICATION_JSON)
  public Response deleteProject(@FormDataParam("assignmentName") String name) {
    Linux linuxApi = new Linux();
    // delete tomcat test file

    String removeZipTestFileCommand = testDir + name + ".zip";
    tomcatService.removeFile(removeZipTestFileCommand);
    // delete db
    deleteAssignmentDatabase(name);

    // delete gitlab
    gitlabService.deleteProjects(name);

    // delete Jenkins

    List<User> users = userService.getStudents();

    for (User user : users) {
      String jobName = jenkins.getJobName(user.getUsername(), name);
      jenkins.deleteJob(jobName);
    }
    return Response.ok().build();
  }

  /**
   * edit projects
   *
   * @param assignmentName project name
   * @return response
   */
  @POST
  @Path("edit")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Produces(MediaType.APPLICATION_JSON)
  public Response editProject(
      @FormDataParam("assignmentName") String assignmentName,
      @FormDataParam("releaseTime") Date releaseTime, @FormDataParam("deadline") Date deadline,
      @FormDataParam("readMe") String readMe, @FormDataParam("file") InputStream file,
      @FormDataParam("file") FormDataContentDisposition fileDetail) {
    int id = dbManager.getAssignmentIdByName(assignmentName);
    if (fileDetail.getFileName() == null) {
      dbManager.editAssignment(deadline, releaseTime, readMe, id);
    } else {
      ProjectTypeEnum assignmentType = dbManager.getAssignmentType(assignmentName);
      final AssignmentType assignment = AssignmentFactory
          .getAssignmentType(assignmentType.getTypeName());

      String tempFilePath = uploadDir + assignmentName;
      String testCasePath = testDir + assignmentName;
      String testCaseZipPath = testCasePath + ".zip";
      // remove current test case
      tomcatService.removeFile(testCaseZipPath);
      tomcatService.storeFileToUploadsFolder(file, assignmentName);

      zipHandler.unzipFile(tempFilePath, testCasePath);
      assignment.createTestCase(testCasePath);
      zipHandler.zipTestFolder(testCasePath);
      long checksum = zipHandler.getChecksum();
      tomcatService.removeFile(uploadDir);
      tomcatService.removeFile(testCasePath);

      dbManager.editAssignment(deadline, releaseTime, readMe, checksum, id);
    }
    return Response.ok().build();
  }

  /**
   * get project checksum
   *
   * @param assignmentName assignment name
   * @return checksum
   */
  @GET
  @Path("checksum")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getProject(@QueryParam("proName") String assignmentName) {
    Assignment assignment = dbManager.getAssignmentByName(assignmentName);
    JSONObject ob = new JSONObject();

    ob.put("testZipUrl", assignment.getTestZipUrl());
    ob.put("testZipChecksum", assignment.getTestZipChecksum());
    System.out.println(ob.toString());
    return Response.ok().entity(ob.toString()).build();
  }

  /**
   * get project checksum
   *
   * @param assignmentName assignment name
   * @return checksum
   */
  @GET
  @Path("getAssignment")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getAssignment(@QueryParam("assignmentName") String assignmentName) {
    Assignment assignment = dbManager.getAssignmentByName(assignmentName);
    JSONObject ob = new JSONObject();
    ob.put("description", assignment.getDescription());
    ob.put("deadline", assignment.getDeadline());
    ob.put("type", assignment.getType());
    return Response.ok().entity(ob.toString()).build();
  }

  /**
   * @return AllAssignments
   */
  @GET
  @Path("getAllAssignments")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getAllAssignments() {
    List<Assignment> assignments = dbManager.getAllAssignment();
    JSONObject ob = new JSONObject();
    ob.put("allAssignments", assignments);
    return Response.ok().entity(ob.toString()).build();
  }

  /**
   * get course name
   *
   * @return course name
   */
  public String getCourseName() {
    String name = "";
    try {
      name = courseConfig.getCourseName();
    } catch (LoadConfigFailureException e) {
      LOGGER.debug(ExceptionUtil.getErrorInfoFromException(e));
      LOGGER.error(e.getMessage());
    }

    return name;
  }

  /**
   * get test folder
   *
   * @param filePath folder directory
   * @return zip file
   */
  @GET
  @Path("getTestFile")
  public Response getTestFile(@QueryParam("filePath") String filePath) {
    File file = new File(filePath);

    ResponseBuilder response = Response.ok((Object) file);
    response.header("Content-Disposition", "attachment;filename=");
    return response.build();
  }

  /**
   * delete Assignment from Database by name
   */
  public void deleteAssignmentDatabase(String name) {

    int aid = dbManager.getAssignmentIdByName(name);
    List<Integer> auidList = auDbManager.getAuids(aid);

    for (int auid : auidList) { // CommitRecord
      List<Integer> cridList = crDbManager.getCommitRecordId(auid);
      for (int crid : cridList) { // ScreenShot
        srDbManager.deleteScreenshotByCrid(crid);
      }
      crDbManager.deleteRecord(auid);
    }
    auDbManager.deleteAssignmentUserByAid(aid);// Assignment_User
    dbManager.deleteAssignment(name);// Assignment

  }

  private void createAssignmentSettings(String username, String assignmentName) {
    ProjectTypeEnum assignmentTypeEnum = dbManager.getAssignmentType(assignmentName);
    AssignmentType assignment = AssignmentFactory
        .getAssignmentType(assignmentTypeEnum.getTypeName());
    addAuid(username, assignmentName);
    try {
      GitlabProject project = gitlabService.createPrivateProject(username, assignmentName, "root");
      gitlabService.setGitlabWebhook(project);
      assignment.createJenkinsJob(username, assignmentName);
    } catch (IOException | LoadConfigFailureException e) {
      LOGGER.debug(ExceptionUtil.getErrorInfoFromException(e));
      LOGGER.error(e.getMessage());
    }
  }

  /**
   * create previous Assignemnt
   *
   * @param username username
   */
  public void createPreviousAssignment(String username) {
    List<String> assignmentNames = dbManager.getAllAssignmentNames();

    for (String assignmentName : assignmentNames) {
      createAssignmentSettings(username, assignmentName);
    }

  }

  /**
   * improvise random student to review different student's hw
   *
   * @param amount number of reviewer
   * @param assignmentName assignment name
   */
  public void randomPairMatching(int amount, String assignmentName) throws SQLException {
    int aid = dbManager.getAssignmentIdByName(assignmentName);
    List<User> userList = userService.getStudents();
    List<AssignmentUser> assignmentUserList = auDbManager.getAssignmentUserListByAid(aid);

    // 1. assign "amount" times of student to review
    for (int count = 0; count < amount; count++) {

      // Reviewer can't be repeat in the same round, this list store who had been assign to review
      List<Integer> storedList = new ArrayList<>();
      List<PairMatching> insertPairMatchingList = new ArrayList<>();

      // When assign the random reviewer, sometimes the last random reviewer and reviewed person
      // is the same, so we need to reassign reviewer in this round
      boolean lastIdSameOrNot = false;

      // 2. assign number of students to different assignment
      for (int eachAu = 0; eachAu < assignmentUserList.size(); eachAu++) {

        // The same assignment_user can't have the repeated reviewer,
        // this list store who have reviewed this assignment_user
        List<Integer> existedReviewerList = pmDbManager
            .getReviewListByAuId(assignmentUserList.get(eachAu).getId());

        // effect as same as lastIdSameOrNot
        int lastImproviseCount = 0;

        while (true) {

          // 3. random reviewer for this assignment_user
          User user = userList.get((int) (Math.random() * userList.size()));
          int reviewId = user.getId();
          boolean storedNotExisted = true;
          boolean reviewerNotExisted = true;

          // 3.1 reviewer can't see his/her own hw
          if (assignmentUserList.get(eachAu).getUid() == reviewId) {
            continue;
          }

          // 3.2 check random reviewer have existed in the existedReviewerList
          for (Integer integer: existedReviewerList) {
            if (integer.equals(reviewId)) {
              reviewerNotExisted = false;
              break;
            }
          }

          // 3.3 check random reviewer have existed in the storedList
          for (Integer integer: storedList) {
            if ( integer.equals(reviewId)) {
              storedNotExisted = false;
              break;
            }
          }

          // 3.4 if it pass the condition, insert to the dateBase
          if ( reviewerNotExisted && storedNotExisted ) {
            PairMatching pairMatching = new PairMatching();
            pairMatching.setAuId(assignmentUserList.get(eachAu).getId());
            pairMatching.setReviewId(reviewId);
            pairMatching.setScoreModeEnum(ReviewStatusEnum.INIT);
            insertPairMatchingList.add(pairMatching);
            storedList.add(reviewId);
            break;
          }

          // 3.5 effect as same as lastIdSameOrNot, the condition to break while loop
          if ((eachAu + 1) == assignmentUserList.size()) {
            if (lastImproviseCount == assignmentUserList.size()) {
              lastIdSameOrNot = true;
              break;
            } else {
              lastImproviseCount++;
            }
          }
        }
      }

      // 4 effect as same as lastIdSameOrNot,
      // if the condition pass, it will insert new pair matching to dataBase
      if (lastIdSameOrNot) {
        count--;
      } else {
        pmDbManager.insertPairMatchingList(insertPairMatchingList);
      }
    }
  }

}
