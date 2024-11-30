package tukano.api;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class UserDB {
	
	@Id
	private String userId;

	private String id;
	private String pwd;
	private String email;
	private String displayName;

	public UserDB() {}
	
	public UserDB(String userId, String pwd, String email, String displayName) {
		this.pwd = pwd;
		this.id = userId;
		this.email = email;
		this.userId = userId;
		this.displayName = displayName;
	}

	public String getUserId() {
		return userId;
	}
	public void setUserId(String userId) {
		this.userId = userId;
	}
	public String getPwd() {
		return pwd;
	}
	public void setPwd(String pwd) {
		this.pwd = pwd;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public String getDisplayName() {
		return displayName;
	}
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
	
	public String userId() {
		return userId;
	}
	
	public String pwd() {
		return pwd;
	}
	
	public String email() {
		return email;
	}
	
	public String displayName() {
		return displayName;
	}
	
	@Override
	public String toString() {
		return "UserDB [userId=" + userId + ", pwd=" + pwd + ", email=" + email + ", displayName=" + displayName + "]";
	}
	
	public UserDB copyWithoutPassword() {
		return new UserDB(userId, "", email, displayName);
	}
	
	public UserDB updateFrom(UserDB other ) {
		return new UserDB( userId,
				other.pwd != null ? other.pwd : pwd,
				other.email != null ? other.email : email, 
				other.displayName != null ? other.displayName : displayName);
	}
}
