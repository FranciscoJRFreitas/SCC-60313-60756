package tukano.impl.data;

public class LikesData {
    private boolean isLiked;

    public LikesData() {}

    public LikesData(boolean value) {
        this.isLiked = value;
    }

    public boolean getValue() {
        return isLiked;
    }

    public void setValue(boolean value) {
        this.isLiked = value;
    }

}

