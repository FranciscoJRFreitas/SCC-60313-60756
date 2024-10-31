package tukano.impl.data;

public class FollowingData {
    private boolean isFollowing;

    public FollowingData() {}

    public FollowingData(boolean isFollowing) {
        this.isFollowing = isFollowing;
    }

    public boolean getIsFollowing() {
        return isFollowing;
    }

    public void setValue(boolean isFollowing) {
        this.isFollowing = isFollowing;
    }

}

