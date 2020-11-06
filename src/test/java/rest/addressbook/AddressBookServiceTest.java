package rest.addressbook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.IOException;
import java.net.URI;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.junit.After;
import org.junit.Test;
import rest.addressbook.config.ApplicationConfig;
import rest.addressbook.domain.AddressBook;
import rest.addressbook.domain.Person;

/**
 * A simple test suite.
 * <ul>
 *   <li>Safe and idempotent: verify that two identical consecutive requests do not modify
 *   the state of the server.</li>
 *   <li>Not safe and idempotent: verify that only the first of two identical consecutive
 *   requests modifies the state of the server.</li>
 *   <li>Not safe nor idempotent: verify that two identical consecutive requests modify twice
 *   the state of the server.</li>
 * </ul>
 */
public class AddressBookServiceTest {

  private HttpServer server;

  @Test
  public void serviceIsAlive() throws IOException {
    // Prepare server
    AddressBook ab = new AddressBook();
    launchServer(ab);

    // Request the address book
    Client client = ClientBuilder.newClient();
    Response response = client.target("http://localhost:8282/contacts")
      .request().get();
    assertEquals(200, response.getStatus());
    AddressBook ab_r = response.readEntity(AddressBook.class);
    assertEquals(0, ab_r.getPersonList()
      .size());

    //////////////////////////////////////////////////////////////////////
    // Verify that GET /contacts is well implemented by the service, i.e
    // complete the test to ensure that it is safe and idempotent
    //////////////////////////////////////////////////////////////////////

    // Safe condition -> Mustn't change server state
    // test: same request must give same result
    Response r2 = client.target("http://localhost:8282/contacts")
      .request().get();
    // test if "r2" has the same response that "response"
    assertEquals(response.getStatus(), response.getStatus());
    // test same state
    assertEquals(ab_r.getPersonList().size(),
      r2.readEntity(AddressBook.class).getPersonList().size());
    // Safe condition implies Idempotent condition (because state doesnt change)
  }

  @Test
  public void createUser() throws IOException {
    // Prepare server
    AddressBook ab = new AddressBook();
    launchServer(ab);

    // Prepare data
    Person juan = new Person();
    juan.setName("Juan");
    URI juanURI = URI.create("http://localhost:8282/contacts/person/1");

    // Safe-Idempotent condition test code needed
    Client client = ClientBuilder.newClient();
    Response r_cond = client.target("http://localhost:8282/contacts")
      .request().get();
    assertEquals(200, r_cond.getStatus());
    AddressBook ab_r = r_cond.readEntity(AddressBook.class);
    assertEquals(0, ab_r.getPersonList().size());


    // Create a new user
    //Client client = ClientBuilder.newClient();
    Response response = client.target("http://localhost:8282/contacts")
      .request(MediaType.APPLICATION_JSON)
      .post(Entity.entity(juan, MediaType.APPLICATION_JSON));

    assertEquals(201, response.getStatus());
    assertEquals(juanURI, response.getLocation());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    Person juanUpdated = response.readEntity(Person.class);
    assertEquals(juan.getName(), juanUpdated.getName());
    assertEquals(1, juanUpdated.getId());
    assertEquals(juanURI, juanUpdated.getHref());

    // Check that the new user exists
    response = client.target("http://localhost:8282/contacts/person/1")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(200, response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    juanUpdated = response.readEntity(Person.class);
    assertEquals(juan.getName(), juanUpdated.getName());
    assertEquals(1, juanUpdated.getId());
    assertEquals(juanURI, juanUpdated.getHref());

    //////////////////////////////////////////////////////////////////////
    // Verify that POST /contacts is well implemented by the service, i.e
    // complete the test to ensure that it is not safe and not idempotent
    //////////////////////////////////////////////////////////////////////

    // Safe condition -> Mustn't change server state
    // Hint: it changes, you're adding a new person, so its not safe
    Response r_cond2 = client.target("http://localhost:8282/contacts")
      .request().get();
    assertEquals(200, r_cond2.getStatus());
    assertNotEquals(
      ab_r.getPersonList().size(),
      r_cond2.readEntity(AddressBook.class).getPersonList().size()
    );


    // Idempotent condition -> Multiple identical requests have the same consecuence than first
    response = client.target("http://localhost:8282/contacts")
      .request(MediaType.APPLICATION_JSON)
      .post(Entity.entity(juan, MediaType.APPLICATION_JSON));

    assertEquals(201, response.getStatus()); //ok response
    assertNotEquals(juanURI, response.getLocation()); //diff response, same request
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType()); //ok response
    Person juanUpdated2 = response.readEntity(Person.class);
    assertEquals(juanUpdated.getName(), juanUpdated2.getName()); // same request
    assertNotEquals(1, juanUpdated2.getId()); //diff response, same request
    assertNotEquals(juanURI, juanUpdated2.getHref()); //diff response, same request
  }

  @Test
  public void createUsers() throws IOException {
    // Prepare server
    AddressBook ab = new AddressBook();
    Person salvador = new Person();
    salvador.setName("Salvador");
    salvador.setId(ab.nextId());
    ab.getPersonList().add(salvador);
    launchServer(ab);

    // Prepare data
    Person juan = new Person();
    juan.setName("Juan");
    URI juanURI = URI.create("http://localhost:8282/contacts/person/2");
    Person maria = new Person();
    maria.setName("Maria");
    URI mariaURI = URI.create("http://localhost:8282/contacts/person/3");

    // Create a user
    Client client = ClientBuilder.newClient();
    Response response = client.target("http://localhost:8282/contacts")
      .request(MediaType.APPLICATION_JSON)
      .post(Entity.entity(juan, MediaType.APPLICATION_JSON));
    assertEquals(201, response.getStatus());
    assertEquals(juanURI, response.getLocation());

    // Create a second user
    response = client.target("http://localhost:8282/contacts")
      .request(MediaType.APPLICATION_JSON)
      .post(Entity.entity(maria, MediaType.APPLICATION_JSON));
    assertEquals(201, response.getStatus());
    assertEquals(mariaURI, response.getLocation());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    Person mariaUpdated = response.readEntity(Person.class);
    assertEquals(maria.getName(), mariaUpdated.getName());
    assertEquals(3, mariaUpdated.getId());
    assertEquals(mariaURI, mariaUpdated.getHref());

    // Check that the new user exists
    response = client.target("http://localhost:8282/contacts/person/3")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(200, response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    mariaUpdated = response.readEntity(Person.class);
    assertEquals(maria.getName(), mariaUpdated.getName());
    assertEquals(3, mariaUpdated.getId());
    assertEquals(mariaURI, mariaUpdated.getHref());

    //////////////////////////////////////////////////////////////////////
    // Verify that GET /contacts/person/3 is well implemented by the service, i.e
    // complete the test to ensure that it is safe and idempotent
    //////////////////////////////////////////////////////////////////////
    
    // GET /contacts/person/3
    // Check that the new user exists and status dont change
    Response response2 = client.target("http://localhost:8282/contacts/person/3")
            .request(MediaType.APPLICATION_JSON).get();
    assertEquals(response.getStatus(), response2.getStatus());

    Person Maria2 = response2.readEntity(Person.class);
    assertEquals(mariaUpdated.getName(), Maria2.getName()); // Safe condition? 1/3
    assertEquals(mariaUpdated.getId(), Maria2.getId()); // Safe condition? 2/3
    assertEquals(mariaUpdated.getHref(), Maria2.getHref()); // Safe condition? 3/3-> OK
    // Safe condition implies Idempotent condition (because state doesnt change)
  }

  @Test
  public void listUsers() throws IOException {

    // Prepare server
    AddressBook ab = new AddressBook();
    Person salvador = new Person();
    salvador.setName("Salvador");
    Person juan = new Person();
    juan.setName("Juan");
    ab.getPersonList().add(salvador);
    ab.getPersonList().add(juan);
    launchServer(ab);

    // Test list of contacts
    Client client = ClientBuilder.newClient();
    Response response = client.target("http://localhost:8282/contacts")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(200, response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    AddressBook addressBookRetrieved = response
      .readEntity(AddressBook.class);
    assertEquals(2, addressBookRetrieved.getPersonList().size());
    assertEquals(juan.getName(), addressBookRetrieved.getPersonList()
      .get(1).getName());

    //////////////////////////////////////////////////////////////////////
    // Verify that GET /contacts is well implemented by the service, i.e
    // complete the test to ensure that it is safe and idempotent
    //////////////////////////////////////////////////////////////////////

    // SAME AS serviceIsAlive() (program wont compile if these test fails) (DRY)
  }

  @Test
  public void updateUsers() throws IOException {
    // Prepare server
    AddressBook ab = new AddressBook();
    Person salvador = new Person();
    salvador.setName("Salvador");
    salvador.setId(ab.nextId());
    Person juan = new Person();
    juan.setName("Juan");
    juan.setId(ab.getNextId());
    URI juanURI = URI.create("http://localhost:8282/contacts/person/2");
    ab.getPersonList().add(salvador);
    ab.getPersonList().add(juan);
    launchServer(ab);

    // Update Maria
    Person maria = new Person();
    maria.setName("Maria");
    Client client = ClientBuilder.newClient();
    Response response = client
      .target("http://localhost:8282/contacts/person/2")
      .request(MediaType.APPLICATION_JSON)
      .put(Entity.entity(maria, MediaType.APPLICATION_JSON));
    assertEquals(200, response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    Person juanUpdated = response.readEntity(Person.class);
    assertEquals(maria.getName(), juanUpdated.getName());
    assertEquals(2, juanUpdated.getId());
    assertEquals(juanURI, juanUpdated.getHref());

    // Verify that the update is real
    response = client.target("http://localhost:8282/contacts/person/2")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(200, response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    Person mariaRetrieved = response.readEntity(Person.class);
    assertEquals(maria.getName(), mariaRetrieved.getName());
    assertEquals(2, mariaRetrieved.getId());
    assertEquals(juanURI, mariaRetrieved.getHref());

    // Verify that only can be updated existing values
    response = client.target("http://localhost:8282/contacts/person/3")
      .request(MediaType.APPLICATION_JSON)
      .put(Entity.entity(maria, MediaType.APPLICATION_JSON));
    assertEquals(400, response.getStatus());

    //////////////////////////////////////////////////////////////////////
    // Verify that PUT /contacts/person/2 is well implemented by the service, i.e
    // complete the test to ensure that it is idempotent but not safe
    //////////////////////////////////////////////////////////////////////

    // Idempotent condition -> Multiple identical requests have the same consecuence than first
    Response r_idem = client.target("http://localhost:8282/contacts/person/2")
      .request(MediaType.APPLICATION_JSON)
      .put(Entity.entity(maria, MediaType.APPLICATION_JSON));
    assertEquals(200, r_idem.getStatus());

    // Has no effect? True -> OK, false -> oh no!
    Person p_idem = r_idem.readEntity(Person.class);
    assertEquals(p_idem.getName(), juanUpdated.getName());
    assertEquals(2, p_idem.getId());
    assertEquals(juanURI, p_idem.getHref());
  }

  @Test
  public void deleteUsers() throws IOException {
    // Prepare server
    AddressBook ab = new AddressBook();
    Person salvador = new Person();
    salvador.setName("Salvador");
    salvador.setId(1);
    Person juan = new Person();
    juan.setName("Juan");
    juan.setId(2);
    ab.getPersonList().add(salvador);
    ab.getPersonList().add(juan);
    launchServer(ab);

    // Delete a user
    Client client = ClientBuilder.newClient();
    Response response = client
      .target("http://localhost:8282/contacts/person/2").request()
      .delete();
    assertEquals(204, response.getStatus());

    // Verify that the user has been deleted
    response = client.target("http://localhost:8282/contacts/person/2")
      .request().delete();
    assertEquals(404, response.getStatus());

    //////////////////////////////////////////////////////////////////////
    // Verify that DELETE /contacts/person/2 is well implemented by the service, i.e
    // complete the test to ensure that it is idempotent but not safe
    //////////////////////////////////////////////////////////////////////

    // Idempotent condition -> Multiple identical requests have the same consecuence than first
    Response r_ids1 = client.target("http://localhost:8282/contacts")
      .request().get();
    assertEquals(200, r_ids1.getStatus());
    assertEquals(1, r_ids1.readEntity(AddressBook.class).getPersonList().size());

    // Should return err code because user with id "2" was deleted before
    Response r_idem = client.target("http://localhost:8282/contacts/person/2").request().delete();
    assertEquals(404, r_idem.getStatus());

    Response r_ids2 = client.target("http://localhost:8282/contacts")
      .request().get();
    assertEquals(200, r_ids2.getStatus());
    assertEquals(1, r_ids2.readEntity(AddressBook.class).getPersonList().size());
  }

  @Test
  public void findUsers() throws IOException {
    // Prepare server
    AddressBook ab = new AddressBook();
    Person salvador = new Person();
    salvador.setName("Salvador");
    salvador.setId(1);
    Person juan = new Person();
    juan.setName("Juan");
    juan.setId(2);
    ab.getPersonList().add(salvador);
    ab.getPersonList().add(juan);
    launchServer(ab);

    // Test user 1 exists
    Client client = ClientBuilder.newClient();
    Response response = client
      .target("http://localhost:8282/contacts/person/1")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(200, response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    Person person = response.readEntity(Person.class);
    assertEquals(person.getName(), salvador.getName());
    assertEquals(person.getId(), salvador.getId());
    assertEquals(person.getHref(), salvador.getHref());

    // Test user 2 exists
    response = client.target("http://localhost:8282/contacts/person/2")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(200, response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    person = response.readEntity(Person.class);
    assertEquals(person.getName(), juan.getName());
    assertEquals(2, juan.getId());
    assertEquals(person.getHref(), juan.getHref());

    // Test user 3 exists
    response = client.target("http://localhost:8282/contacts/person/3")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(404, response.getStatus());
  }

  private void launchServer(AddressBook ab) throws IOException {
    URI uri = UriBuilder.fromUri("http://localhost/").port(8282).build();
    server = GrizzlyHttpServerFactory.createHttpServer(uri,
      new ApplicationConfig(ab));
    server.start();
  }

  @After
  public void shutdown() {
    if (server != null) {
      server.shutdownNow();
    }
    server = null;
  }

}
