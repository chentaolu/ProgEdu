package fcu.selab.progedu.setting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebAssignmentSetting extends SettingZipHandler implements AssignmentSettings {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(WebAssignmentSetting.class);

  public WebAssignmentSetting(String name) {
    super("web", name);
  }

  @Override
  public void createAssignmentSetting(List<String> order,String name) {
    Map<String, Object> mainObject = new LinkedHashMap<String, Object>();
    Map<String,String> scriptObject = new LinkedHashMap<String, String>();
    Map<String,String> dependenciesObject = new LinkedHashMap<String, String>();
    Gson gson = new Gson();
      
    PrintWriter pw;
    try {
      pw = new PrintWriter ("/package.json");
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
          .replace(":", ": ")
          );
      pw.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }

}