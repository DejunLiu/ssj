/*
 * SVM.java
 * Copyright (c) 2016
 * Authors: Ionut Damian, Michael Dietz, Frank Gaibler, Daniel Langerenken, Simon Flutura
 * *****************************************************
 * This file is part of the Social Signal Interpretation for Java (SSJ) framework
 * developed at the Lab for Human Centered Multimedia of the University of Augsburg.
 *
 * SSJ has been inspired by the SSI (http://openssi.net) framework. SSJ is not a
 * one-to-one port of SSI to Java, it is an approximation. Nor does SSJ pretend
 * to offer SSI's comprehensive functionality and performance (this is java after all).
 * Nevertheless, SSJ borrows a lot of programming patterns from SSI.
 *
 * This library is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this library; if not, see <http://www.gnu.org/licenses/>.
 */

package hcm.ssj.ml;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import hcm.ssj.core.Cons;
import hcm.ssj.core.Log;
import hcm.ssj.core.option.OptionList;
import hcm.ssj.core.stream.Stream;
import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_problem;

/**
 * Evaluates live data depending on the naive bayes classifier files of SSI.<br>
 * Created by Frank Gaibler on 22.09.2015.
 */
public class SVM extends Model
{
    /**
     * All options for the transformer
     */
    public class Options extends OptionList
    {
        /**
         *
         */
        private Options()
        {
            addOptions();
        }
    }

    public final Options options = new Options();
    private int n_classes;
    private int n_features;
    private String[] class_names = null;
    private double[] max = null;
    private double[] min = null;
    private svm_model model;
    private svm_node[] nodes;
    double[] prob_estimates;
    float[] probs;

    private final int SVM_SCALE_UPPER = 1;
    private final int SVM_SCALE_LOWER = -1;

    /**
     *
     */
    public SVM()
    {
        _name = this.getClass().getSimpleName();
    }

    private void init()
    {
        nodes = new svm_node[n_features+1];
        for(int i = 0; i < nodes.length; i++)
            nodes[i] = new svm_node();

        prob_estimates = new double[n_classes];
        probs = new float[n_classes];
    }

    /**
     * @param stream Stream
     * @return double[]
     */
    protected float[] forward(Stream[] stream)
    {
        if (stream.length != 1)
        {
            Log.w("only one input stream currently supported, consider using merge");
            return null;
        }
        if (!_isTrained)
        {
            Log.w("not trained");
            return null;
        }
        if (stream[0].dim != n_features)
        {
            Log.w("feature dimension differs");
            return null;
        }
        if (stream[0].type != Cons.Type.FLOAT) {
            Log.w ("invalid stream type");
            return null;
        }

        float[] ptr = stream[0].ptrF();
        for (int i = 0; i < n_features; i++) {
            nodes[i].index = i + 1;
            nodes[i].value = ptr[i];
        }
        nodes[n_features].index=-1;

        scale_instance (nodes, n_features);
        svm.svm_predict_probability (model, nodes, prob_estimates);

        //normalization
        float sum = 0;
        for (int i = 0; i < n_classes; i++) {
            probs[model.label[i]] = (float) prob_estimates[i];
            sum += probs[model.label[i]];
        }

        for (int j = 0; j < n_classes; j++) {
            probs[j]/=sum;
        }

        return probs;
    }

    @Override
    void train(Stream[] stream) {
        Log.e("training not supported yet");
    }

    /**
     * Load data from option file
     */
    protected void loadOption(File file)
    {
    }

    @Override
    void save(File file) {
        Log.e("saving not supported yet");
    }

    @Override
    int getNumClasses() {
        return n_classes;
    }

    @Override
    String[] getClassNames() {
        return class_names;
    }

    /**
     * Load data from model file
     */
    protected void load(File file)
    {
        if (file == null)
        {
            Log.e("model file not set in options");
            return;
        }
        BufferedReader reader = null;
        try
        {
            InputStream inputStream = new FileInputStream(file);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            reader = new BufferedReader(inputStreamReader);

            //skip comments
            String line;
            do {
                line = reader.readLine();
            } while (line.startsWith("#") || line.charAt(0) == '\n');

            //read num class and num features
            String token[] = line.split("\t");
            if (token.length > 0) {
                n_classes = Integer.valueOf(token[0]);
            } else {
                throw new IOException("can't read number of classes from classifier file " + file.getName() + "!");
            }
            if (token.length > 1) {
                n_features = Integer.valueOf(token[1]);
            } else {
                throw new IOException("can't read feature dimension from classifier file " + file.getName() + "'!");
            }

            //skip comments
            do {
                line = reader.readLine();
            } while (line.startsWith("#") || line.charAt(0) == '\n');

            // read class names
            class_names = line.split(" ");

            max = new double[n_features];
            min = new double[n_features];
            if(scan(reader, "# Scaling: max\tmin") == null)
                throw new IOException("can't read scaling information for SVM classifier from " + file.getName());

            for (int i = 0; i < n_features; i++) {

                line = reader.readLine();
                token = line.split("\t");
                if (token.length != 2) {
                    throw new IOException("misformed scaling information for SVM classifier from " + file.getName());
                }

                max[i] = Double.valueOf(token[0]);
                min[i] = Double.valueOf(token[1]);
            }

            do {
                line = reader.readLine();
            } while (line.isEmpty() || line.startsWith("#") || line.charAt(0) == '\n');

            //read SVM model
            model = svm.svm_load_model(reader);
        }
        catch (FileNotFoundException e)
        {
            Log.e("file not found");
            return;
        }
        catch (IOException e)
        {
            Log.e("error reading SVM model", e);
            return;
        }
        finally {
            try
            {
                reader.close();
            }
            catch(IOException | NullPointerException e)
            {
                Log.e("error closing reader", e);
            }
        }

        init();
        _isTrained = true;
    }

    /**
     * @param reader BufferedReader
     * @return String
     */
    private String scan(BufferedReader reader, String msg)
    {
        String line;
        try
        {
            do {
                line = reader.readLine();
                if (line.contains(msg))
                    return line;
            } while(line != null);
        }
        catch (IOException e)
        {
            Log.e("could not read line");
        }
        return null;
    }

    void create_scaling (svm_problem problem, int n_features, double[] _max, double[] _min) {

        int i,j, idx;
        double temp;

        for (i=0;i<n_features;i++){
            _max[i]=-Double.MAX_VALUE;
            _min[i]=Double.MAX_VALUE;
        }

        for (i=0;i<problem.l;i++) {
            idx=0;
            for (j=0;j<n_features;j++) {
                if (problem.x[i][idx].index != j+1)
                    temp=0;
                else {
                    temp=problem.x[i][idx].value;
                    idx++;
                }

                if (temp < _min[j])
                    _min[j] = temp;
                if (temp > _max[j])
                    _max[j] = temp;
            }
        }
    }

    void scale_instance (svm_node[] instance, int n_features) {

        int j=0, idx=0, n_idx=0;
        double temp;
        svm_node[] usInstance = new svm_node[n_features+1];
        for(int i = 0; i < usInstance.length; i++)
            usInstance[i] = new svm_node();

        while (instance[j].index != -1) {
            usInstance[j].index=instance[j].index;
            usInstance[j].value=instance[j].value;
            j++;
        }
        usInstance[j].index=-1;

        for (j=0;j<n_features;j++) {
            if (usInstance[idx].index != j+1)
                temp=0;
            else
                temp=usInstance[idx++].value;
            if (max[j]-min[j] != 0)
                temp=SVM_SCALE_LOWER+(SVM_SCALE_UPPER-SVM_SCALE_LOWER)*(temp-min[j])/(max[j]-min[j]);
            else
                temp=SVM_SCALE_LOWER+(SVM_SCALE_UPPER-SVM_SCALE_LOWER)*(temp-min[j])/-Float.MAX_VALUE;
            if (temp != 0) {
                instance[n_idx].index=j+1;
                instance[n_idx++].value=temp;
            }
        }
        instance[n_idx].index=-1;
    }
}
