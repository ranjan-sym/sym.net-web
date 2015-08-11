import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by ranjan on 7/24/15.
 */
public class ExampleServlet extends HttpServlet {
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

    response.setContentType("text/plain");
    response.setStatus(HttpServletResponse.SC_OK);

    response.getWriter().println("hello world");
  }
}
