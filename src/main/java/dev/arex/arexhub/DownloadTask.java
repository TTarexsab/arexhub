package dev.arex.arexhub;

public class DownloadTask {
    public enum Status { DOWNLOADING, DONE, FAILED }

    public volatile Status status = Status.DOWNLOADING;
    public volatile float progress = 0f; // 0..1, -1 = bilinmiyor
    public volatile String error = "";
}
