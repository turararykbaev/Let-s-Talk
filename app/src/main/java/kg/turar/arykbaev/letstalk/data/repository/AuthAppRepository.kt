package kg.turar.arykbaev.letstalk.data.repository

import androidx.lifecycle.MutableLiveData
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.StorageReference
import kg.turar.arykbaev.letstalk.domain.*
import kg.turar.arykbaev.letstalk.domain.models.User
import javax.inject.Inject

class AuthAppRepository @Inject constructor(
    private val mAuth: FirebaseAuth,
    private val refDatabaseRoot: DatabaseReference,
    private val refStorageRoot: StorageReference
) {

    private val event: MutableLiveData<Event> = MutableLiveData()

    private val uid: String
        get() = mAuth.currentUser?.uid.toString()

    fun getEvent() = event

    private fun uploadImage(user: User) {
        val path = refStorageRoot.child(FOLDER_PROFILE_IMAGE).child(uid)

        path.putFile(user.uri!!)
            .addOnSuccessListener {
                user.image_url = it.task.result.storage.downloadUrl.toString()
                refDatabaseRoot.child(NODE_USERS).child(uid).updateChildren(getDateMap(user))
                    .addOnSuccessListener {
                        sendVerification()
                    }
                    .addOnFailureListener { exception ->
                        event.value = Event.Notification(exception.message.toString())
                    }
            }
            .addOnFailureListener {
                event.value = Event.Notification(it.message.toString())
            }
    }

    private fun getDateMap(user: User): Map<String, Any> {
        val dateMap = mutableMapOf<String, Any>()
        dateMap[CHILD_ID] = uid
        dateMap[CHILD_NAME] = user.name
        dateMap[CHILD_EMAIL] = user.email
        dateMap[CHILD_DEPARTMENT] = user.department
        dateMap[CHILD_GRADE] = user.grade
        dateMap[CHILD_GENDER] = user.gender
        dateMap[CHILD_FROM] = user.from
        dateMap[CHILD_IMAGE_URL] = user.image_url
        dateMap[CHILD_NATIVE_LANG] = user.nativeLang
        dateMap[CHILD_LEARN_LANG] = user.learningLang
        dateMap[CHILD_STATE] = UserState.OFFLINE.state
        return dateMap
    }

    fun register(user: User) {
        event.value = Event.Loading()
        mAuth.createUserWithEmailAndPassword(user.email, user.password)
            .addOnSuccessListener {
                uploadImage(user)
            }
            .addOnFailureListener {
                event.value = Event.Notification(it.message.toString())
            }
    }

    private fun sendVerification() {
        mAuth.currentUser.sendEmailVerification()
            .addOnSuccessListener {
                event.value = Event.Success()
            }
            .addOnFailureListener {
                event.value = Event.Notification(it.message.toString())
            }
    }

    fun login(email: String, password: String) {
        mAuth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                when (isValidUser) {
                    true -> {
                        event.value = Event.Success()
                        changeUserState(UserState.ONLINE)
                    }
                    else -> event.value = Event.Notification("Please, Verify your email")
                }
            }
            .addOnFailureListener {
                event.value = Event.Notification(it.message.toString())
            }
    }

    fun logout() {
        changeUserState(UserState.OFFLINE)
        mAuth.signOut()
    }

    fun checkAuthentication() {
        when (isValidUser) {
            true -> event.value = Event.Notification("Welcome!")
            else -> event.value = Event.Unauthorized()
        }
    }

    fun checkEmail(email: String) {
        mAuth.fetchSignInMethodsForEmail(email).addOnCompleteListener {
            when (it.result?.signInMethods?.isEmpty()) {
                true -> event.value = Event.Success()
                false -> event.value = Event.Notification("This email address already exists")
            }
        }
    }

    fun initUser() {
        refDatabaseRoot.child(NODE_USERS).child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val user = snapshot.getValue(User::class.java) ?: User()
                    event.value = Event.Success(user)
                }

                override fun onCancelled(error: DatabaseError) {
                }
            })
    }

    fun changeUserState(state: UserState) {
        if (isValidUser) refDatabaseRoot.child(NODE_USERS).child(uid).child(CHILD_STATE).setValue(state.state)
    }

    private val isValidUser: Boolean
        get() = mAuth.currentUser != null && mAuth.currentUser?.isEmailVerified == true
}