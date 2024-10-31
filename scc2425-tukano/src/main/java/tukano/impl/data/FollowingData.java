package tukano.impl.data;

public class FollowingData {
    private boolean isFollowing;

    public FollowingData() {}

    public FollowingData(boolean value) {
        this.isFollowing = value;
    }

    public boolean getValue() {
        return isFollowing;
    }

    public void setValue(boolean value) {
        this.isFollowing = value;
    }

}

