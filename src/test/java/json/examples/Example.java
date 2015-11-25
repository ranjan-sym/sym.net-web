package json.examples;

import javax.ws.rs.*;

/**
 * Created by ranjan on 7/24/15.
 */
@Path("/example")
public class Example {
  @GET
  @Path("/get/{description}")
  @Produces("application/json")
  public POJO get(@PathParam("description") String description) {

    POJO p = new POJO();
    p.setName("POJO Name");
    p.description = "This is what you said: " + description;
    return p;
    //return "{\"0\":\"Hello World\"}";
  }

  @POST
  @Path("/post/{attrib}")
  @Consumes("application/json")
  @Produces("application/json")
  public POJO post(String data, @PathParam("attrib") String attrib) {
    POJO pojo = new POJO();
    pojo.setName(attrib);
    pojo.description = data;
    return pojo;
  }

  @GET
  @Path("/getJson")
  @Produces("application/json")
  public String getJson() {
    return "{\"name\":\"hello\"}";
  }

  @GET
  @Path("/getBool")
  @Produces("application/json")
  public boolean getBool() {
    return true;
  }
}
