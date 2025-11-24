package step.examples.loadtesting.playwright;

import step.handlers.javahandler.Keyword;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

public class JavaKeywords extends AbstractJavaLibraryKeyword {

    public String AP_VERSION="0.0.0-SNAPSHOT updated 20/11 14h39";

    @Keyword
    public void SimpleJavaKeyword() throws IOException {
        output.add("Lib_version_used", USING_LIB_VERSION);
        output.add("AP_version_used", AP_VERSION);
        if (!this.isInAutomationPackage()) {
            output.setError("Not in an AP");
        } else {
            File file = retrieveAndExtractAutomationPackage();
            if (file.isDirectory()) {
                output.add("directory files", Files.list(file.toPath()).collect(Collectors.toList()).toString());
            }
        }
    }
}
