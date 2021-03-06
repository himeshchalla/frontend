package idapiclient

import com.gu.identity.model.{EmailList, LiftJsonConfig, Subscriber, User}
import client.{Anonymous, Auth, Parameters, Response}
import client.connection.{Http, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import client.parser.{JodaJsonSerializer, JsonBodyParser}
import idapiclient.responses.{AccountDeletionResult, CookiesResponse}
import client.connection.util.{ApiHelpers, ExecutionContexts}
import conf.IdConfig
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.Serialization.write
import utils.SafeLogging
import idapiclient.requests.{PasswordUpdate, TokenPassword}


abstract class IdApi(http: Http, jsonBodyParser: JsonBodyParser, conf: IdConfig)
  extends IdApiUtils with SafeLogging with ApiHelpers {

  override val apiRootUrl: String = conf.apiRoot
  override val clientAuth: Auth = ClientAuth(conf.apiClientToken)

  import jsonBodyParser.{extractUnit, extract}

  implicit def executionContext: ExecutionContext
  implicit val formats = LiftJsonConfig.formats + new JodaJsonSerializer

  def jsonField(field: String)(json: JValue): JValue = json \ field

  def extractUser: (client.Response[HttpResponse]) => client.Response[User] = extract(jsonField("user"))

  // AUTH
  def authBrowser(userAuth: Auth, trackingData: TrackingData, persistent: Option[Boolean] = None): Future[Response[CookiesResponse]] = {
    val params = buildParams(None, Some(trackingData), Seq("format" -> "cookies") ++ persistent.map("persistent" -> _.toString))
    val headers = buildHeaders(Some(userAuth))
    val body = write(userAuth)
    val response = http.POST(apiUrl("auth"), Some(body), params, headers)
    response map extract(jsonField("cookies"))
  }

  def unauth(auth: Auth, trackingData: TrackingData): Future[Response[CookiesResponse]] =
    post("unauth", Some(auth), Some(trackingData)) map extract[CookiesResponse](jsonField("cookies"))

  // USERS

  def user(userId: String, auth: Auth = Anonymous): Future[Response[User]] = {
    val apiPath = urlJoin("user", userId)
    val params = buildParams(Some(auth))
    val headers = buildHeaders(Some(auth))
    val response = http.GET(apiUrl(apiPath), params, headers)
    response map extractUser
  }

  def userFromVanityUrl(vanityUrl: String, auth: Auth = Anonymous): Future[Response[User]] = {
    val apiPath = urlJoin("user", "vanityurl", vanityUrl)
    val params = buildParams(Some(auth))
    val headers = buildHeaders(Some(auth))
    val response = http.GET(apiUrl(apiPath), params, headers)
    response map extractUser
  }

  def saveUser(userId: String, user: UserUpdate, auth: Auth): Future[Response[User]] =
    post(urlJoin("user", userId), Some(auth), body = Some(write(user))) map extractUser

  def me(auth: Auth): Future[Response[User]] = {
    val apiPath = urlJoin("user", "me")
    val params = buildParams(Some(auth))
    val response = http.GET(apiUrl(apiPath), params, buildHeaders(Some(auth)))
    response map extractUser
  }

  /**
   * data to save to a subdocument in the user's record
   * The path param provides the subdocument to be saved to e.g. prefs.myApp
   */
  def updateUser(userId: String, auth: Auth, trackingData: TrackingData, path: String, data: JValue): Future[Response[User]] = {
    val pathParts = path.split('.').toList
    post(urlJoin("user" :: userId :: pathParts : _*), Some(auth), Some(trackingData), Some(write(data))) map extractUser
  }

  def updateUser(user: User, auth: Auth, trackingData: TrackingData): Future[Response[User]] =
    post("user", Some(auth), Some(trackingData), Some(write(user))) map extractUser

  def register(user: User, trackingParameters: TrackingData, returnUrl: Option[String] = None): Future[Response[User]] = {
    val userData = write(user)
    val params = buildParams(tracking = Some(trackingParameters), extra = returnUrl.map(url => Iterable("returnUrl" -> url)))
    val headers = buildHeaders(extra = trackingParameters.ipAddress.map(ip => Iterable("X-Forwarded-For" -> ip)))
    val response = http.POST(apiUrl("user"), Some(userData), params, headers)
    response map extractUser
  }

  // PASSWORD RESET/UPDATE

  def passwordExists( auth: Auth ): Future[Response[Boolean]] = {
    val apiPath = urlJoin("user", "password-exists")
    val response = http.GET(apiUrl(apiPath), buildParams(Some(auth)), buildHeaders(Some(auth)))
    response map extract[Boolean](jsonField("passwordExists"))
  }

  def updatePassword(pwdUpdate: PasswordUpdate, auth: Auth, trackingData: TrackingData ): Future[Response[Unit]] = {
    val apiPath = urlJoin("user", "password")
    val body = write(pwdUpdate)
    val response = post(apiPath, Some(auth), Some(trackingData), Some(body))
    response map extractUnit
  }

  def userForToken( token : String ): Future[Response[User]] = {
    val apiPath = urlJoin("pwd-reset", "user-for-token")
    val params = buildParams(extra = Iterable("token" -> token))
    val response = http.GET(apiUrl(apiPath), params, buildHeaders())
    response map extractUser
  }

  def resetPassword( token : String, newPassword : String ): Future[Response[Unit]] = {
    val apiPath = urlJoin("pwd-reset", "reset-pwd-for-user")
    val postBody = write(TokenPassword(token, newPassword))
    val response = http.POST(apiUrl(apiPath), Some(postBody), clientAuth.parameters, clientAuth.headers)
    response map extractUnit
  }

  def sendPasswordResetEmail(emailAddress : String, trackingParameters: TrackingData): Future[Response[Unit]] = {
    val apiPath = urlJoin("pwd-reset", "send-password-reset-email")
    val params = buildParams(tracking = Some(trackingParameters), extra = Iterable("email-address" -> emailAddress, "type" -> "reset"))
    val response = http.GET(apiUrl(apiPath), params, buildHeaders())
    response map extractUnit
  }

  // EMAILS

  def userEmails(userId: String, trackingParameters: TrackingData): Future[Response[Subscriber]] = {
    val apiPath = urlJoin("useremails", userId)
    val params = buildParams(tracking = Some(trackingParameters))
    val response = http.GET(apiUrl(apiPath), params, buildHeaders())
    response map extract(jsonField("result"))
  }

  def addSubscription(userId: String, emailList: EmailList, auth: Auth, trackingParameters: TrackingData): Future[Response[Unit]] = {
    post(urlJoin("useremails", userId, "subscriptions"), Some(auth), Some(trackingParameters), Some(write(emailList))) map extractUnit
  }

  def deleteSubscription(userId: String, emailList: EmailList, auth: Auth, trackingParameters: TrackingData): Future[Response[Unit]] = {
    delete(urlJoin("useremails", userId, "subscriptions"), Some(auth), Some(trackingParameters), Some(write(emailList))) map extractUnit
  }

  def updateUserEmails(userId: String, subscriber: Subscriber, auth: Auth, trackingParameters: TrackingData): Future[Response[Unit]] =
    post(urlJoin("useremails", userId), Some(auth), Some(trackingParameters), Some(write(subscriber))) map extractUnit

  def validateEmail(token: String, trackingParameters: TrackingData): Future[Response[Unit]] =
    post(urlJoin("user","validate-email", token), trackingParameters = Some(trackingParameters)) map extractUnit

  def resendEmailValidationEmail(auth: Auth, trackingParameters: TrackingData): Future[Response[Unit]] =
    post("user/send-validation-email", Some(auth), Some(trackingParameters)) map extractUnit

  def deleteTelephone(auth: Auth): Future[Response[Unit]] =
    delete("user/me/telephoneNumber", Some(auth)) map extractUnit

  // THIRD PARTY SIGN-IN
  def addUserToGroup(groupCode: String, auth: Auth): Future[Response[Unit]] = {
    post(urlJoin("user", "me", "group", groupCode), Some(auth)) map extractUnit
  }

  def executeAccountDeletionStepFunction(userId: String, email: String, reason: Option[String], auth: Auth): Future[Response[AccountDeletionResult]] = {
    case class DeletionBody(identityId: String, email: String, reason: Option[String])
    http.POST(
        s"${conf.accountDeletionApiRoot}/delete",
        Some(write(DeletionBody(userId, email, reason))),
        headers = buildHeaders(Some(auth), extra = Seq(("x-api-key", conf.accountDeletionApiKey)))
    ) map extract[AccountDeletionResult](identity)
  }

  def post(apiPath: String,
           auth: Option[Auth] = None,
           trackingParameters: Option[TrackingData] = None,
           body: Option[String] = None): Future[Response[HttpResponse]] =
    http.POST(apiUrl(apiPath), body, buildParams(auth, trackingParameters), buildHeaders(auth))

  def delete(apiPath: String,
           auth: Option[Auth] = None,
           trackingParameters: Option[TrackingData] = None,
           body: Option[String] = None): Future[Response[HttpResponse]] =
    http.DELETE(apiUrl(apiPath), body, buildParams(auth, trackingParameters), buildHeaders(auth))
}

class SynchronousIdApi(http: Http, jsonBodyParser: JsonBodyParser, conf: IdConfig)
    extends IdApi(http, jsonBodyParser, conf) {
  implicit def executionContext: ExecutionContext = ExecutionContexts.currentThreadContext
}

trait IdApiUtils {
  val apiRootUrl: String
  val clientAuth: Auth

  implicit object ParamsOpt2Params extends (Option[Parameters] => Parameters) {
    def apply(paramsOpt: Option[Parameters]): Parameters = paramsOpt.getOrElse(Iterable.empty)
  }

  protected def buildParams(auth: Option[Auth] = None,
                            tracking: Option[TrackingData] = None,
                            extra: Parameters = Iterable.empty): Parameters = {
    extra ++ clientAuth.parameters ++
      auth.map(_.parameters) ++
      tracking.map({ trackingData =>
        trackingData.parameters ++ trackingData.ipAddress.map(ip => "ip" -> ip)
      })
  }

  protected def buildHeaders(auth: Option[Auth] = None, extra: Parameters = Iterable.empty): Parameters = {
    extra ++ clientAuth.headers ++ auth.map(_.headers)
  }

  protected def apiUrl(path: String) = urlJoin(apiRootUrl, path)

  protected def urlJoin(pathParts: String*) = {
    pathParts.filter(_.nonEmpty).map(slug => {
      slug.stripPrefix("/").stripSuffix("/")
    }) mkString "/"
  }
}
