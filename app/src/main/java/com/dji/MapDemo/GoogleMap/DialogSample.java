package com.dji.MapDemo.GoogleMap;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;



/**
 * Created by mogun on 30/11/2017.
 */

public class DialogSample extends AppCompatDialogFragment {

    private EditText editTextAltitude;
    private EditText editTextSpeed;
    private double speed;
    private double altitude;
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.layout_dialog, null);

        builder.setView(view).setTitle("Flight Parameters")
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                })
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {

                         speed = Double.parseDouble(editTextSpeed.getText().toString());
                         altitude = Double.parseDouble(editTextAltitude.getText().toString());

                        String Text1 = Double.toString(speed);
                        String Text2 = Double.toString(altitude);

                        Toast.makeText(getContext(), Text1 + " , " + Text2, Toast.LENGTH_SHORT).show();

                    }
                });
        editTextAltitude = view.findViewById(R.id.edit_altitude);
        editTextSpeed = view.findViewById(R.id.edit_speed);
        return builder.create();


    }


    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);
        try {

        } catch (ClassCastException e)
        {
            throw new ClassCastException(context.toString() +  "must implement Example Dialog Listener");
        }
    }

    public double returnSpeed()
    {
        return this.speed;
    }

    public  double returnAltitude()
    {
        return  this.altitude;
    }




}
