import json.examples.Example;
import net.symplifier.web.WebServer;
import net.symplifier.web.WebServerException;
import org.junit.Test;

/**
 * Created by ranjan on 7/23/15.
 */
public class TestWebApplication {

  @Test
  public void testWebServer() throws WebServerException, InterruptedException {
    WebServer webServer = new WebServer(8888);
    webServer.setServlet("/example", new ExampleServlet());
    webServer.setContextFromResource("/", "www");
    webServer.setJsonServlet("/json", Example.class.getPackage());
    webServer.start();

    Thread.currentThread().join();
  }

}
