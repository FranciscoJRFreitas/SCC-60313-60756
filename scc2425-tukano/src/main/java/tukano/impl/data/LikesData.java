package tukano.impl.data;

public class LikesData {
    private boolean isLiked;

    public LikesData() {}

    public LikesData(boolean isLiked) {
        this.isLiked = isLiked;
    }

    public boolean getIsLiked() {
        return isLiked;
    }

    public void setValue(boolean isLiked) {
        this.isLiked = isLiked;
    }

}

