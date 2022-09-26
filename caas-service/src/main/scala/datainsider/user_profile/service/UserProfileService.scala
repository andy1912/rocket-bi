package datainsider.user_profile.service

import com.twitter.inject.Logging
import com.twitter.util.{Future, Return, Throw}
import datainsider.client.domain.user.UserProfile
import datainsider.client.exception.{BadRequestError, EmailVerificationRequiredError, InternalError, NotFoundError, ProfileNotFoundError}
import datainsider.profiler.Profiler
import datainsider.user_caas.domain.Page
import datainsider.user_caas.domain.UserType.UserType
import datainsider.user_caas.service.UserService
import datainsider.user_profile.controller.http.request.EditProfileRequest
import datainsider.user_profile.domain.PagingResult
import datainsider.user_profile.repository.UserProfileRepository
import org.apache.http.auth.AuthenticationException

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * @author sonpn.
  * @edit andy
  */

trait UserProfileService {

  def deleteUserProfile(organizationId: Long, username: String): Future[Boolean]

  def getUserProfileByEmail(organizationId: Long, email: String): Future[UserProfile]

  def findProfileByEmail(organizationId: Long, email: String): Future[Option[UserProfile]]

  def getUserProfile(organizationId: Long, username: String): Future[Option[UserProfile]]

  @throws[NotFoundError]("if user not found")
  def updateUserProperties(organizationId: Long, username: String, properties: Map[String, String], deletedPropertyKeys: Set[String]): Future[UserProfile]

  def getUserEmail(organizationId: Long, username: String): Future[Option[String]]

  def getVerifiedUserProfile(organizationId: Long, username: String): Future[UserProfile]

  def getUserProfiles(organizationId: Long, usernames: Seq[String]): Future[Map[String, UserProfile]]

  def listActiveUserDetails(organizationId: Long, from: Int, size: Int): Future[Page[UserProfile]]

  @deprecated("Use SearchUsers with keywords instead of")
  def searchUsers(organizationId: Long, isActive: Option[Boolean], from: Int, size: Int): Future[Page[UserProfile]]

  def getAllUserProfiles(organizationId: Long): Future[Map[String, UserProfile]]

  def createProfile(organizationId: Long, userProfile: UserProfile): Future[UserProfile]

  def updateProfile(organizationId: Long, username: String, request: EditProfileRequest): Future[UserProfile]

  def isConfirmEmail(organizationId: Long, username: String): Future[Boolean]

  def isProfileByEmailExisted(organizationId: Long, email: String): Future[Boolean]

  def changePassword(organizationId: Long, username: String, oldPass: String, newPass: String): Future[Boolean]

  def searchUsers(
      organizationId: Long,
      keyword: String,
      userType: Option[UserType],
      from: Option[Int] = None,
      size: Option[Int] = None
  ): Future[PagingResult[UserProfile]]
}

case class UserProfileServiceImpl @Inject() (
    userService: UserService,
    profileRepository: UserProfileRepository
) extends UserProfileService
    with Logging {

  override def isProfileByEmailExisted(organizationId: Long, email: String): Future[Boolean] =
    Profiler(s"[UserProfile] ${this.getClass.getSimpleName}::isProfileByEmailExisted") {
      findProfileByEmail(organizationId, email).map {
        case Some(_) => true
        case None    => false
      }
    }

  override def isConfirmEmail(organizationId: Long, username: String): Future[Boolean] =
    Profiler(s"[UserProfile] ${this.getClass.getSimpleName}::isConfirmEmail") {
      Future {
        profileRepository.getProfile(organizationId, username).map(_.alreadyConfirmed) match {
          case Some(value) => value
          case None        => throw BadRequestError(s"profile of $username does not exist")
        }
      }
    }

  override def createProfile(organizationId: Long, profile: UserProfile): Future[UserProfile] =
    Profiler(s"[UserProfile] ${this.getClass.getSimpleName}::createProfile") {
      Future {
        profileRepository.createProfile(organizationId, profile)
        profileRepository.getProfile(organizationId, profile.username) match {
          case Some(value) => value
          case None        => throw InternalError(s"create profile failed $profile")
        }
      }
    }

  override def updateProfile(
      organizationId: Long,
      username: String,
      request: EditProfileRequest
  ): Future[UserProfile] =
    Profiler(s"[UserProfile] ${this.getClass.getSimpleName}::updateProfile") {
      Future {
        val oldProfile: UserProfile = fetchProfile(organizationId, username)
        val newProfile: UserProfile = request.buildFrom(oldProfile)
        profileRepository.updateProfile(organizationId, username, newProfile)
        fetchProfile(organizationId, username)
      }
    }

  override def updateUserProperties(organizationId: Long, username: String, properties: Map[String, String], deletedPropertyKeys: Set[String]): Future[UserProfile] =
    Profiler("[UserProfile] ${this.getClass.getSimpleName}::updateUserProperties") {
      Future {
        val oldProfile = fetchProfile(organizationId, username)
        val allProperties: Map[String, String] = oldProfile.properties.getOrElse(Map.empty) ++ properties
        val finalProperties: Map[String, String] = allProperties.filterNot(property => deletedPropertyKeys.contains(property._1))
        val newProfile = oldProfile.copy(properties = Option(finalProperties), updatedTime = Some(System.currentTimeMillis()))
        profileRepository.updateProfile(organizationId, username, newProfile)
        newProfile
      }
  }

  override def deleteUserProfile(organizationId: Long, username: String): Future[Boolean] =
    Profiler(s"[UserProfile] ${this.getClass.getSimpleName}::deleteUserProfile") {
      Future {
        profileRepository.deleteProfile(organizationId, username)
      }
    }

  override def findProfileByEmail(organizationId: Long, email: String): Future[Option[UserProfile]] =
    Profiler(s"[UserProfile] ${this.getClass.getSimpleName}::findProfileByEmail") {
      Future {
        profileRepository.getProfileByEmail(organizationId, email)
      }
    }

  override def getUserProfileByEmail(organizationId: Long, email: String): Future[UserProfile] =
    Profiler(s"[UserProfile] ${this.getClass.getSimpleName}::getUserProfileByEmail") {
      findProfileByEmail(organizationId, email).map {
        case Some(profile) => profile
        case _             => throw ProfileNotFoundError("No profile was found for this email.")
      }
    }

  override def getUserProfile(organizationId: Long, username: String): Future[Option[UserProfile]] =
    Profiler(s"[UserProfile] ${this.getClass.getSimpleName}::getUserProfile") {
      Future {
        profileRepository.getProfile(organizationId, username)
      }
    }
  override def getUserEmail(organizationId: Long, username: String): Future[Option[String]] =
    Profiler(s"[UserProfile] ${this.getClass.getSimpleName}::getUserEmail") {
      getUserProfile(organizationId, username).map { profileOpt =>
        profileOpt.flatMap(_.email)
      }
    }

  override def getVerifiedUserProfile(organizationId: Long, username: String): Future[UserProfile] =
    Profiler(s"[UserProfile] ${this.getClass.getSimpleName}::getVerifiedUserProfile") {
      def ensureUserVerified(profile: Option[UserProfile]): UserProfile = {
        profile match {
          case Some(profile) =>
            if (!profile.alreadyConfirmed)
              throw EmailVerificationRequiredError("You have to verify your email.")
            else profile
          case None => throw NotFoundError(s"profile not found.")
        }
      }
      getUserProfile(organizationId, username).map(ensureUserVerified)
    }

  override def getUserProfiles(organizationId: Long, usernames: Seq[String]): Future[Map[String, UserProfile]] =
    Profiler(s"[UserProfile] ${this.getClass.getSimpleName}::getUserProfiles") {
      Future {
        profileRepository.getProfiles(organizationId, usernames)
      }
    }

  override def getAllUserProfiles(organizationId: Long): Future[Map[String, UserProfile]] =
    Profiler(s"[UserProfile] ${this.getClass.getSimpleName}::getAllUserProfiles") {
      Future {
        profileRepository.getAllUserProfiles(organizationId)
      }
    }

  override def listActiveUserDetails(organizationId: Long, from: Int, size: Int): Future[Page[UserProfile]] =
    Profiler(s"[UserProfile] ${this.getClass.getSimpleName}::listActiveUserDetails") {
      searchUsers(organizationId, Some(true), from, size)
    }

  override def searchUsers(
      organizationId: Long,
      isActive: Option[Boolean],
      from: Int,
      size: Int
  ): Future[Page[UserProfile]] =
    Profiler(s"[UserProfile] ${this.getClass.getSimpleName}::searchUsers") {
      for {
        userIds <- userService.listUserIds(organizationId, isActive, from, size).transform {
          case Return(r) => Future.value(r)
          case Throw(e)  => Future.value(Page.empty[String])
        }
        profileMap <- getUserProfiles(organizationId, userIds.data)
      } yield buildUserDetailPageResult(userIds, profileMap)
    }

  private def buildUserDetailPageResult(
      userIds: Page[String],
      profileMap: Map[String, UserProfile]
  ): Page[UserProfile] =
    Profiler(s"[UserProfile] ${this.getClass.getSimpleName}::buildUserDetailPageResult") {
      val userProfiles = userIds.data.map { username =>
        profileMap.getOrElse(username, UserProfile(username))
      }
      Page(userIds.total, userProfiles)
    }

  override def searchUsers(
      organizationId: Long,
      keyword: String,
      userType: Option[UserType],
      from: Option[Int],
      size: Option[Int]
  ): Future[PagingResult[UserProfile]] =
    Profiler(s"[UserProfile] ${this.getClass.getSimpleName}::searchUsers") {
      Future {
        profileRepository.searchUsers(organizationId, keyword, userType, from, size)
      }
    }

  override def changePassword(
      organizationId: Long,
      username: String,
      oldPass: String,
      newPass: String
  ): Future[Boolean] =
    Profiler(s"[UserProfile] ${this.getClass.getSimpleName}::changePassword") {
      userService.isPasswordCorrect(organizationId, username, oldPass).flatMap {
        case true  => userService.resetPassword(organizationId, username, newPass)
        case false => throw new AuthenticationException("Wrong password")
      }
    }

  @throws[NotFoundError]("if the profile is not found")
  private def fetchProfile(organizationId: Long, username: String): UserProfile =
    Profiler(s"[UserProfile] ${this.getClass.getSimpleName}::fetchProfile") {
      profileRepository.getProfile(organizationId, username) match {
        case Some(value) => value
        case None        => throw NotFoundError(s"profile for $username is not found.")
      }
    }
}