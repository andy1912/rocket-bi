package datainsider.user_profile.controller.http.request

trait PagingRequest {
  val from: Option[Int]
  val size: Option[Int]
}
