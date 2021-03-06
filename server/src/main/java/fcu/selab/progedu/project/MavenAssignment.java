package fcu.selab.progedu.project;

import java.io.File;
import java.io.IOException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fcu.selab.progedu.config.CourseConfig;
import fcu.selab.progedu.config.GitlabConfig;
import fcu.selab.progedu.conn.JenkinsService;
import fcu.selab.progedu.db.AssignmentDbManager;
import fcu.selab.progedu.exception.LoadConfigFailureException;
import fcu.selab.progedu.service.StatusService;
import fcu.selab.progedu.status.StatusEnum;
import fcu.selab.progedu.data.ZipFileInfo;
import fcu.selab.progedu.utils.ZipHandler;
import fcu.selab.progedu.utils.ExceptionUtil;

public class MavenAssignment extends AssignmentType {
  private static final Logger LOGGER = LoggerFactory.getLogger(MavenAssignment.class);

  @Override
  public ProjectTypeEnum getProjectType() {
    return ProjectTypeEnum.MAVEN;
  }

  @Override
  public String getSampleTemplate() {
    return "MavenQuickStart.zip";
  }

  @Override
  public String getJenkinsJobConfigSample() {
    return "config_maven.xml";
  }

  @Override
  public void createJenkinsJobConfig(String username, String projectName) {
    try {
      GitlabConfig gitlabConfig = GitlabConfig.getInstance();
      String jenkinsJobConfigPath = this.getClass()
          .getResource("/jenkins/" + getJenkinsJobConfigSample()).getPath();

      CourseConfig courseConfig = CourseConfig.getInstance();
      String progEduApiUrl = courseConfig.getTomcatServerIp() + courseConfig.getBaseuri()
          + "/webapi";
      String projectUrl = gitlabConfig.getGitlabHostUrl() + "/" + username + "/" + projectName
          + ".git";

      DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
      docFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
      DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

      String updateDbUrl = progEduApiUrl + "/commits/update";
      String checksumUrl = progEduApiUrl + "/assignment/checksum?proName=" + projectName;
      String testFileUrl = AssignmentDbManager.getInstance().getTestFileUrl(projectName);

      Document doc = docBuilder.parse(jenkinsJobConfigPath);
      String jobName = username + "_" + projectName;

      doc.getElementsByTagName("url").item(0).setTextContent(projectUrl);
      doc.getElementsByTagName("jobName").item(0).setTextContent(jobName);
      doc.getElementsByTagName("testFileName").item(0).setTextContent(projectName);
      doc.getElementsByTagName("proDetailUrl").item(0).setTextContent(checksumUrl);
      doc.getElementsByTagName("progeduDbUrl").item(0).setTextContent(updateDbUrl);
      doc.getElementsByTagName("testFileUrl").item(0).setTextContent(testFileUrl);
      doc.getElementsByTagName("user").item(0).setTextContent(username);
      doc.getElementsByTagName("proName").item(0).setTextContent(projectName);

      // write the content into xml file
      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Transformer transformer = transformerFactory.newTransformer();
      DOMSource source = new DOMSource(doc);
      StreamResult result = new StreamResult(new File(jenkinsJobConfigPath));
      transformer.transform(source, result);
    } catch (LoadConfigFailureException | ParserConfigurationException | SAXException | IOException
        | TransformerException e) {
      LOGGER.debug(ExceptionUtil.getErrorInfoFromException(e));
      LOGGER.error(e.getMessage());
    }

  }

  @Override
  public StatusEnum checkStatusType(int num, String username, String assignmentName) {
    StatusEnum status;
    StatusService statusService = StatusService.getInstance();
    if (statusService.isInitialization(num)) {
      status = StatusEnum.INITIALIZATION;
    } else {
      JenkinsService jenkinsService = JenkinsService.getInstance();
      String jobName = username + "_" + assignmentName;
      String console = jenkinsService.getConsole(jobName, num);

      if (statusService.isBuildSuccess(console)) {
        status = StatusEnum.BUILD_SUCCESS;
      } else if (statusService.isMavenUnitTestFailure(console)) {
        status = StatusEnum.UNIT_TEST_FAILURE;
      } else if (statusService.isMavenCheckstyleFailure(console)) {
        status = StatusEnum.CHECKSTYLE_FAILURE;
      } else if (statusService.isMavenCompileFailureOfUnitTest(console)) {
        status = StatusEnum.COMPILE_FAILURE_OF_UNIT_TEST; 
      } else {
        status = StatusEnum.COMPILE_FAILURE;
      }
    }
    return status;
  }

  @Override
  public void createTemplate(String cloneDirectoryPath) {
    try {
      FileUtils.deleteDirectory(new File(cloneDirectoryPath + "/src/test"));
    } catch (IOException e) {
      LOGGER.debug(ExceptionUtil.getErrorInfoFromException(e));
      LOGGER.error(e.getMessage());
    }
  }

  @Override
  public ZipFileInfo createTestCase(String testDirectory) {
    ZipHandler zipHandler;
    ZipFileInfo zipFileInfo = null;

    try {
      FileUtils.deleteDirectory(new File(testDirectory + "/src/main"));
    } catch (IOException e) {
      LOGGER.debug(ExceptionUtil.getErrorInfoFromException(e));
      LOGGER.error(e.getMessage());
    }

    try {
      zipHandler = new ZipHandler();
      zipFileInfo = zipHandler.getZipInfo(testDirectory);
    } catch (LoadConfigFailureException e) {
      LOGGER.debug(ExceptionUtil.getErrorInfoFromException(e));
      LOGGER.error(e.getMessage());
    }
    return zipFileInfo;
  }

}
