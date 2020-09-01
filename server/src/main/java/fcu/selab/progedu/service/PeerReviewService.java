package fcu.selab.progedu.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import fcu.selab.progedu.conn.GitlabService;
import fcu.selab.progedu.data.Assignment;
import fcu.selab.progedu.data.PairMatching;
import fcu.selab.progedu.data.ReviewSetting;
import fcu.selab.progedu.data.User;
import fcu.selab.progedu.db.AssignmentDbManager;
import fcu.selab.progedu.db.AssignmentUserDbManager;
import fcu.selab.progedu.db.CommitRecordDbManager;
import fcu.selab.progedu.db.PairMatchingDbManager;
import fcu.selab.progedu.db.ReviewSettingDbManager;
import fcu.selab.progedu.db.UserDbManager;
import fcu.selab.progedu.utils.ExceptionUtil;

import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabProject;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("peerReview")
public class PeerReviewService {
  private GitlabService gitlabService = GitlabService.getInstance();
  private CommitRecordDbManager commitRecordDbManager = CommitRecordDbManager.getInstance();
  private AssignmentUserDbManager assignmentUserDbManager = AssignmentUserDbManager.getInstance();
  private AssignmentDbManager assignmentDbManager = AssignmentDbManager.getInstance();
  private ReviewSettingDbManager reviewSettingDbManager = ReviewSettingDbManager.getInstance();
  private PairMatchingDbManager pairMatchingDbManager = PairMatchingDbManager.getInstance();
  private UserDbManager userDbManager = UserDbManager.getInstance();

  private static final Logger LOGGER = LoggerFactory.getLogger(PeerReviewService.class);

  /**
   * get all commit result which is assigned by peer review
   */
  @GET
  @Path("record/allUsers")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getAllReviewedRecord() {
    Response response = null;
    try {
      JSONArray array = new JSONArray();
      JSONObject result = new JSONObject();
      List<User> users = getStudents();
      for (User user: users) {
        String username = user.getUsername();
        Response userCommitRecord = getReviewedRecord(username);
        JSONObject ob = new JSONObject();
        ob.put("name", user.getName());
        ob.put("username", user.getUsername());
        ob.put("display", user.getDisplay());
        ob.put("commitRecord", new JSONArray(userCommitRecord.getEntity().toString()));
        array.put(ob);
      }
      result.put("allReviewedRecord", array);

      response = Response.ok(result.toString()).build();
    } catch (Exception e) {
      LOGGER.debug(ExceptionUtil.getErrorInfoFromException(e));
      LOGGER.error(e.getMessage());
      response = Response.serverError().build();
    }

    return response;
  }

  /**
   *  get one user commit result which is assigned by peer review
   *
   * @param username user name
   */
  @GET
  @Path("record/oneUser")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getReviewedRecord(@QueryParam("username") String username) {
    Response response = null;
    try {
      List<Assignment> assignmentList = assignmentDbManager.getAllReviewAssignment();
      int userId = userDbManager.getUserIdByUsername(username);
      JSONArray array = new JSONArray();

      for (Assignment assignment: assignmentList) {
        int auId = assignmentUserDbManager.getAuid(assignment.getId(), userId);
        ReviewSetting reviewSetting = reviewSettingDbManager.getReviewSetting(assignment.getId());
        JSONObject ob = new JSONObject();
        int commitRecordCount = commitRecordDbManager.getCommitCount(auId);
        ob.put("assignmentName", assignment.getName());
        ob.put("releaseTime", assignment.getReleaseTime());
        ob.put("deadline", assignment.getDeadline());
        ob.put("commitRecordCount", commitRecordCount);
        ob.put("reviewReleaseTime", reviewSetting.getReleaseTime());
        ob.put("reviewDeadline", reviewSetting.getDeadline());
        ob.put("reviewStatus", reviewedRecordStatus(auId, commitRecordCount));
        array.put(ob);
      }
      response = Response.ok(array.toString()).build();
    } catch (Exception e) {
      LOGGER.debug(ExceptionUtil.getErrorInfoFromException(e));
      LOGGER.error(e.getMessage());
      response = Response.serverError().entity(e.getMessage()).build();
    }

    return response;
  }

  /**
   * get all user's status of reviewing other's hw
   */
  @GET
  @Path("status/allUsers")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getAllReviewStatus() {
    Response response = null;

    try {
      JSONArray array = new JSONArray();
      JSONObject result = new JSONObject();
      List<User> users = getStudents();
      for (User user: users) {
        String username = user.getUsername();
        Response reviewStatus = getReviewStatus(username);
        JSONObject ob = new JSONObject();
        ob.put("username", username);
        ob.put("name", user.getName());
        ob.put("display", user.getDisplay());
        ob.put("reviewStatus", new JSONArray(reviewStatus.getEntity().toString()));
        array.put(ob);
      }

      result.put("allReviewStatus", array);
      response = Response.ok(result.toString()).build();
    } catch (Exception e) {
      LOGGER.debug(ExceptionUtil.getErrorInfoFromException(e));
      LOGGER.error(e.getMessage());
      response = Response.serverError().entity(e.getMessage()).build();
    }

    return response;
  }

  /**
   *
   * @param username user name
   */
  @GET
  @Path("status/oneUser")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getReviewStatus(@QueryParam("username") String username) {
    Response response = null;
    try {
      List<Assignment> assignmentList = assignmentDbManager.getAllReviewAssignment();
      int reviewId = userDbManager.getUserIdByUsername(username);
      JSONArray array = new JSONArray();

      for (Assignment assignment: assignmentList) {
        ReviewSetting reviewSetting = reviewSettingDbManager.getReviewSetting(assignment.getId());
        JSONObject ob = new JSONObject();
        ob.put("assignmentName", assignment.getName());
        ob.put("amount", reviewSetting.getAmount());
        ob.put("releaseTime", assignment.getReleaseTime());
        ob.put("deadline", assignment.getDeadline());
        ob.put("reviewReleaseTime", reviewSetting.getReleaseTime());
        ob.put("reviewDeadline", reviewSetting.getDeadline());
        ob.put("count", getReviewCompletedCount(assignment.getId(), reviewId));
        ob.put("status", reviewerStatus(assignment.getId(),
            reviewId, reviewSetting.getAmount()).getTypeName());
        array.put(ob);
      }

      response = Response.ok(array.toString()).build();
    } catch (Exception e) {
      LOGGER.debug(ExceptionUtil.getErrorInfoFromException(e));
      LOGGER.error(e.getMessage());
      response = Response.serverError().entity(e.getMessage()).build();
    }

    return response;
  }

  /**
   *
   */
  @GET
  @Path("sourceCode")
  public Response getSourceCode(@QueryParam("username") String username,
                                @QueryParam("assignmentName") String assignmentName) {
    Response response = null;

    try {
      GitlabProject gitlabProject = gitlabService.getProject(username, assignmentName);
      System.out.println(gitlabProject.getId());
      System.out.println(gitlabProject.getName());
      GitlabAPI gitlabApi = gitlabService.getGitlab();
      System.out.println(gitlabApi.getAllProjects());
      byte[] buffer = gitlabApi.getFileArchive(gitlabProject);

      response = Response.ok(buffer).type("application/zip")
          .header("Content-Disposition", "attachment; filename=\"test.zip\"").build();
    } catch (Exception e) {
      LOGGER.debug(ExceptionUtil.getErrorInfoFromException(e));
      LOGGER.error(e.getMessage());
      response = Response.serverError().entity(e.getMessage()).build();
    }

    return response;
  }

  /**
   * check which reviewed status of specific assignment_user
   *
   * @param auId assignment_user id
   * @param commitRecordCount commit record count
   */
  public String reviewedRecordStatus(int auId, int commitRecordCount)
      throws SQLException {
    List<PairMatching> pairMatchingList = pairMatchingDbManager.getPairMatchingByAuId(auId);
    String resultStatus = "INIT";

    if (commitRecordCount == 1) {
      return resultStatus;
    }

    for (PairMatching pairMatching: pairMatchingList) {
      if (pairMatching.getReviewStatusEnum().equals(ReviewStatusEnum.UNCOMPLETED)) {
        resultStatus = "DONE";
        break;
      } else if (pairMatching.getReviewStatusEnum().equals(ReviewStatusEnum.COMPLETED)) {
        resultStatus = "REVIEWED";
      }
    }

    return resultStatus;
  }

  /**
   *  check reviewer status of his/her review job
   *
   * @param aid assignment id
   * @param reviewId user id
   */
  public ReviewStatusEnum reviewerStatus(int aid, int reviewId, int amount) throws SQLException {
    List<PairMatching> pairMatchingList =
        pairMatchingDbManager.getPairMatchingByAidAndReviewId(aid, reviewId);
    ReviewStatusEnum resultStatus = ReviewStatusEnum.INIT;
    int initCount = 0;

    for (PairMatching pairMatching: pairMatchingList) {
      if (pairMatching.getReviewStatusEnum().equals(ReviewStatusEnum.UNCOMPLETED)) {
        resultStatus = ReviewStatusEnum.UNCOMPLETED;
        break;
      } else if (pairMatching.getReviewStatusEnum().equals(ReviewStatusEnum.COMPLETED)) {
        resultStatus = ReviewStatusEnum.COMPLETED;
      } else if (pairMatching.getReviewStatusEnum().equals(ReviewStatusEnum.INIT)) {
        initCount++;
      }
    }

    if (initCount == amount) {
      resultStatus = ReviewStatusEnum.INIT;
    }

    return resultStatus;
  }

  /**
   * get count about how many hw have reviewer reviewed
   *
   * @param aid assignment id
   * @param reviewId user id
   */
  public int getReviewCompletedCount(int aid, int reviewId) throws SQLException {
    List<PairMatching> pairMatchingList =
        pairMatchingDbManager.getPairMatchingByAidAndReviewId(aid, reviewId);
    int count = 0;

    for (PairMatching pairMatching: pairMatchingList) {
      if (pairMatching.getReviewStatusEnum().equals(ReviewStatusEnum.COMPLETED)) {
        count++;
      }
    }

    return count;
  }

  /**
   * Get all user which role is student
   *
   * @return all GitLab users
   */
  public List<User> getStudents() {
    List<User> studentUsers = new ArrayList<>();
    List<User> users = userDbManager.getAllUsers();

    for (User user : users) {
      if (user.getRole().contains(RoleEnum.STUDENT)) {
        studentUsers.add(user);
      }
    }
    return studentUsers;
  }
}
