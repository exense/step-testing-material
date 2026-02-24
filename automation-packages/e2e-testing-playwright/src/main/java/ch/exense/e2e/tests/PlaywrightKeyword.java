package ch.exense.e2e.tests;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.SelectOption;
import step.grid.io.AttachmentHelper;
import step.handlers.javahandler.AbstractKeyword;
import step.handlers.javahandler.Input;
import step.handlers.javahandler.Keyword;
import step.streaming.common.QuotaExceededException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;

public class PlaywrightKeyword extends AbstractKeyword {

    private Playwright playwright;
    private Browser browser;
    private Page page;
    private BrowserContext context;
    private Video video;

    @Keyword(name = "Buy MacBook in OpenCart")
    public void buyMacBookInOpenCart(@Input(name = "shouldFail", required = true) boolean shouldFail) throws InterruptedException {
        page.navigate("https://opencart-prf.stepcloud.ch/");
        page.locator("text=MacBook").click();
        page.locator("text=Add to Cart").click();
        page.locator("text=1 item").click();
        if(shouldFail) {
            page.locator("text=Not existing").click();
        } else {
            page.locator("text=View Cart").click();
        }
        page.locator("//a[text()='Checkout']").click();
        page.locator("text=Guest Checkout").click();
        Thread.sleep(500);
        page.locator("#button-account").click();
        // Read the Firstname and Lastname from the Keyword Inputs
        page.locator("#input-payment-firstname").type("John");
        page.locator("#input-payment-lastname").type("Doe");
        page.locator("#input-payment-email").type("customer@opencart.demo");
        page.locator("#input-payment-telephone").type("+41777777777");
        page.locator("#input-payment-address-1").type("Bahnhofstrasse 1");
        page.locator("#input-payment-city").type("Zurich");
        page.locator("#input-payment-postcode").type("8001");
        page.locator("#input-payment-country").selectOption(new SelectOption().setLabel("Switzerland"));
        page.locator("#input-payment-zone").selectOption(new SelectOption().setLabel("Zürich"));
        page.locator("#button-guest").click();
    }
    
    @Override
    public void beforeKeyword(String keywordName, Keyword annotation) {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
        context = browser.newContext(new Browser.NewContextOptions().setRecordVideoDir(Paths.get("videos/")).setRecordVideoSize(640,480));
        page = context.newPage();
        video = page.video();
        Tracing.StartOptions startOptions = new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true);
        context.tracing().start(startOptions);
    }

    @Override
    public boolean onError(Exception e) {
        // Take a screenshot of the page and attach it to the output
        if (page != null) {
            byte[] screenshot = page.screenshot();
            output.addAttachment(AttachmentHelper.generateAttachmentFromByteArray(screenshot, "screenshot.png"));
        }
        // Attach the playwright trace to the output, using classical and streamed attachment
        if (context != null) {
            try {
                Path path = Paths.get("streamed_trace.zip");
                try {
                    context.tracing().stop(new Tracing.StopOptions().setPath(path));
                    liveReporting.fileUploads.startBinaryFileUpload(path.toFile(), "application/vnd.step.playwright-trace+zip").complete();
                    output.addAttachment(AttachmentHelper.generateAttachmentFromByteArray(Files.readAllBytes(path), "trace.zip", "application/vnd.step.playwright-trace+zip"));
                } catch (QuotaExceededException | ExecutionException | InterruptedException ex) {
                    throw new RuntimeException(ex);
                } finally {
                    path.toFile().delete();
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        if (e.getCause() != null && (e.getCause() instanceof com.microsoft.playwright.PlaywrightException ||
                                     e.getCause() instanceof org.opentest4j.AssertionFailedError)) {
            output.setBusinessError(e.getCause().getMessage());
            return false;
        } else {
            return super.onError(e);
        }
    }

    @Override
    public void afterKeyword(String keywordName, Keyword annotation) {
        // Ensure Playwright is properly closed after each keyword execution
        // to avoid process leaks on the agent
        playwright.close();
        //Write video as attachment
        if (video != null) {
            Path path = video.path();
            try {
                output.addAttachment(AttachmentHelper.generateAttachmentFromByteArray(Files.readAllBytes(path), "video.webm"));
                video.delete();
                if (path.toFile().exists()) {
                    path.toFile().delete();
                }
            } catch (IOException e) {
                logger.error("Unable to attach playwright video from file " + path.getFileName().toString() , e);
            }
        }
    }
}
