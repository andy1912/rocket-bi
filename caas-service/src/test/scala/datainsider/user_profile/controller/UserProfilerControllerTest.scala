package datainsider.user_profile.controller

import datainsider.login_provider.controller.DataInsiderServer
import datainsider.user_caas.domain.UserType
import datainsider.user_profile.controller.http.request.SuggestionUserRequest
import datainsider.user_profile.util.JsonParser
import org.apache.http.HttpStatus
import org.scalatest.BeforeAndAfterAll

class UserProfilerControllerTest extends DataInsiderServer with BeforeAndAfterAll {
  var authToken: Option[String] = None

  override def beforeAll(): Unit = {
    super.beforeAll();
    login()
  }


  test("Suggest user") {
    val body = SuggestionUserRequest("user", Some(0), Some(20), Some(UserType.User), null)
    val response = server.httpPost("/user/profile/suggest", postBody = JsonParser.toJson(body), headers = Map("Authorization" -> getToken()))
    assertResult(HttpStatus.SC_OK)(response.statusCode)
  }
}
