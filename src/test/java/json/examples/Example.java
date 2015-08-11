package json.examples;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

/**
 * Created by ranjan on 7/24/15.
 */
@Path("/example")
public class Example {
  @GET
  @Path("/get")
  @Produces("application/json")
  public POJO get() {
    POJO p = new POJO();
    p.setName("POJO Name");
    return p;
    //return "{\"0\":\"Hello World\"}";
  }
}
