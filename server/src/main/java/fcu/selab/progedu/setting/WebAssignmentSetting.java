package fcu.selab.progedu.setting;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileNotFoundException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WebAssignmentSetting extends SettingZipHandler implements AssignmentSettings {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(WebAssignmentSetting.class);

  public WebAssignmentSetting(String name) {
    super("web", name);
  }

  @Override
  public void unZipAssignmenToTmp() {
    super.unZipAssignmenToTmp();
  }

  @Override
  public void packUpAssignment() {
    super.packUpAssignment();
  }

  @Override
  public void createAssignmentSetting(List<String> order,String name) {
    
    List <String> commands = new ArrayList<String>();
    commands = getCommand(orders);

    try {
      File inputFile = new File("..\\config_web.xml");         
      DocumentBuilderFactory docFactory =
          DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = 
          docFactory.newDocumentBuilder();
      Document doc = docBuilder.parse(inputFile);
      doc.getDocumentElement().normalize();
      
      NodeList builders = doc.getElementsByTagName("builders");
      System.out.println(builders.getLength());
      
      NodeList value = (NodeList) builders.item(0);
      Element value2 = (Element) builders.item(0);
      
      int BuildStepWithTimeoutCount = value.getLength();
      while (BuildStepWithTimeoutCount > 5 ) {
        Node wannaDel = (Node) value2.getElementsByTagName
            ("hudson.plugins.build__timeout.BuildStepWithTimeout").item(1);
        value2.removeChild(wannaDel);
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT,"yes");
        transformer.transform(
            new DOMSource(doc), new StreamResult(new File("..\\config_web1.xml")));

        System.out.println(value.getLength());
        BuildStepWithTimeoutCount = BuildStepWithTimeoutCount-2;
      }
      
      for (String command: commands) {
        Element TimeOut = doc.createElement("timeoutMinutes");
        TimeOut.appendChild(doc.createTextNode("15"));
        Element Strategy = doc.createElement("strategy");
        Strategy.setAttribute("class", 
            "hudson.plugins.build_timeout.impl.AbsoluteTimeOutStrategy");
        Strategy.appendChild(TimeOut);
          
        Element NodeCommand = doc.createElement("command");
        NodeCommand.appendChild(doc.createTextNode(command));
        Element BuildStep = doc.createElement("buildStep");
        BuildStep.setAttribute("class", "hudson.tasks.Shell");
        BuildStep.appendChild(NodeCommand);
          
        Element FailOperation = doc.createElement
            ("hudson.plugins.build__timeout.operations.FailOperation");
        Element Operation = doc.createElement("operationList");
        Operation.appendChild(FailOperation);
          
        Element BuildStepWithTimeout = doc.createElement
            ("hudson.plugins.build__timeout.BuildStepWithTimeout");
        BuildStepWithTimeout.setAttribute("plugin", "build-timeout@1.19");
        BuildStepWithTimeout.appendChild(Strategy);
        BuildStepWithTimeout.appendChild(BuildStep);
        BuildStepWithTimeout.appendChild(Operation);
          
        builders.item(builders.getLength()-1).appendChild(BuildStepWithTimeout);
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT,"yes");
        transformer.transform(new DOMSource(doc), 
            new StreamResult(new File("..\\config_web1.xml")));
      }
    } catch (Exception e) {
      LOGGER.debug(ExceptionUtil.getErrorInfoFromException(e));
      LOGGER.error(e.getMessage());
    }

    /*
    Map<String, Object> mainObject = new LinkedHashMap<String, Object>();
    Map<String,String> scriptObject = new LinkedHashMap<String, String>();
    Map<String,String> dependenciesObject = new LinkedHashMap<String, String>();
    Gson gson = new Gson();
      
    PrintWriter pw;
    try {
      pw = new PrintWriter("/package.json");
      mainObject.put("name", name);
      mainObject.put("version", "1.0.0");
      mainObject.put("description", "sample");
      mainObject.put("main", "index.js");
      
      scriptObject.put("test", "node_modules/.bin/mocha ./src/test/*.js --timeout 100000");
      scriptObject.put("htmlhint", "htmlhint ./src/web/");
      scriptObject.put("stylelint", "stylelint ./src/web/");
      scriptObject.put("eslint", "eslint --ext .html");
          
      mainObject.put("scripts", scriptObject);
      mainObject.put("author", "");
      mainObject.put("license", "ISC");
          
      dependenciesObject.put("eslint-config-airbnb", "*");
      dependenciesObject.put("eslint-plugin-html", "*");
      dependenciesObject.put("eslint-plugin-jsx-a11y", "*");
      dependenciesObject.put("eslint-plugin-react", "*");
      dependenciesObject.put("htmlhint", "*");
      dependenciesObject.put("npm", "*");
      dependenciesObject.put("selenium-webdriver", "*");
      dependenciesObject.put("eslint", "*");
      dependenciesObject.put("eslint-config-airbnb-base", "*");
      dependenciesObject.put("eslint-plugin-import", "*");
      dependenciesObject.put("chai", "^4.2.0");
      dependenciesObject.put("mocha", "^6.0.2");
      dependenciesObject.put("stylelint", "*");
      dependenciesObject.put("stylelint-config-standard", "*");
          
      mainObject.put("dependencies", dependenciesObject);
      String json = gson.toJson(mainObject, LinkedHashMap.class);
      pw.write(json
          .replace("{", "\n{\n")
          .replace("}", "\n}\n")
          .replace(",", ",\n")
          .replace(":", ": "));
      pw.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }*/
  }

  private List<String> getCommand(List<String> orders) {
    List <String> commands = new ArrayList<String>();
    commands.add("npm i");

    for (String order : orders) {
      if (order.equals("HTML Failure")) {
        commands.add("npm run htmlhint");
      } else if (order.equals("CSS Failure")) {
        commands.add("npm run stylelint");
      } else if(order.equals("JavaScript Failure")) {
        commands.add("npm run eslint");
      } else if(order.equals("Unit Test Failure")) {
        commands.add("npm run test");
      }
    }
    return commands;
  }
}