package fr.jeremiegarcia.metaosc;

import android.widget.CompoundButton;

import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.module.Led;

/**
 * Created by jgarcia on 31/03/16.
 */
public class LedOnCkeckedChangeListener implements CompoundButton.OnCheckedChangeListener {

    MetaWearBoard board;
    Led.ColorChannel channel;
    Led ledModule = null;


    public LedOnCkeckedChangeListener(MetaWearBoard mwBoard, Led.ColorChannel colorChannel) {
        this.board = mwBoard;
        this.channel = colorChannel;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (ledModule == null) {
            try {
                ledModule = this.board.getModule(Led.class);
            } catch (UnsupportedModuleException e) {
                e.printStackTrace();
            }
        }

        if (ledModule != null) {
            if (isChecked) {
                configureChannel(ledModule.configureColorChannel(this.channel));
                ledModule.play(true);
            } else {
                ledModule.stop(true);
            }
        }
    }

    private void configureChannel(Led.ColorChannelEditor editor) {
        final short PULSE_WIDTH= 1000;
        editor.setHighIntensity((byte) 31).setLowIntensity((byte) 31)
                .setHighTime((short) (PULSE_WIDTH >> 1)).setPulseDuration(PULSE_WIDTH)
                .setRepeatCount((byte) -1).commit();
    }
}
