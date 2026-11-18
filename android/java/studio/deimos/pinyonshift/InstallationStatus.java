package studio.deimos.pinyonshift;

import android.app.Activity;
import android.text.format.Formatter;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Shared progress for folder import and verification in the Game files page. */
final class InstallationStatus extends LinearLayout {
    private final TextView status;
    private final ProgressBar progress;

    InstallationStatus(Activity activity, String description) {
        this(activity, "Installation status", description);
    }

    InstallationStatus(Activity activity, String title, String description) {
        super(activity);
        setOrientation(VERTICAL);
        int padding = FestivalUi.dp(activity, 12);
        setPadding(padding, padding, padding, padding);
        FestivalUi.glassBackground(activity, this, false, 14);
        addView(FestivalUi.text(activity, title, 16, FestivalUi.TEXT));
        status = FestivalUi.text(activity, description, 13, FestivalUi.MUTED);
        addView(status);
        progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setVisibility(View.GONE);
        addView(progress, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    void update(String message, long done, long total) {
        status.setText(total <= 0 ? message : message + "\n" + Formatter.formatFileSize(getContext(), done)
            + " / " + Formatter.formatFileSize(getContext(), total));
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(total <= 0);
        if (total > 0) progress.setProgress((int) Math.min(1000, done * 1000 / total));
    }

    void finish(String message, boolean success) {
        status.setText(message);
        progress.setIndeterminate(false);
        progress.setProgress(success ? 1000 : 0);
        progress.setVisibility(success ? View.VISIBLE : View.GONE);
    }
}
