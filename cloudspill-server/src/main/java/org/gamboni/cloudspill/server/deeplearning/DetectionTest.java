package org.gamboni.cloudspill.server.deeplearning;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration.ListBuilder;
import org.deeplearning4j.nn.conf.distribution.UniformDistribution;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Sgd;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.io.CharStreams;

public class DetectionTest {
	
    public static void main(String[] args) throws IOException {
    	
    	Multimap<Integer, String> tags = HashMultimap.create();
    	
    	File imagePath = new File(args[0]);
    	File flagPath = new File(args[1]);
    	
    	System.out.println("Parsing tag list");
    	// skip header line
    	for (String line : Iterables.skip(CharStreams.readLines(new FileReader(flagPath)), 1)) {
    		int tab = line.indexOf('\t');
    		int id = Integer.parseInt(line.substring(0, tab));
    		String tag = line.substring(tab+1);
    		
    		tags.put(id, tag);
    	}
    	System.out.println("Found "+ tags.size() +" tags");
    	List<String> tagValues = new ArrayList<>(tags.values());
    	
    	final File[] fileList = imagePath.listFiles();
    	
    	System.out.println("Found "+ fileList.length +" files.");
    	
    	final int trainingCount = fileList.length / 2;
    	System.out.println("Building training set with first "+ trainingCount);
		final int inputLayerSize = 50*50*3;
		// One row per image
		INDArray input = Nd4j.zeros(trainingCount, inputLayerSize);
		final int outputLayerSize = tagValues.size();
		INDArray labels = Nd4j.zeros(trainingCount, outputLayerSize);
		
		IntStream.range(0, trainingCount).forEach(index -> {
			File file = fileList[index];
			int id = Integer.parseInt(file.getName().substring(0, file.getName().indexOf('.')));
			final BufferedImage image = load(file);
			/* Dump image data in input neurons */
			IntStream.range(0, 50).forEach(y -> IntStream.range(0, 50).forEach(x -> {
				final int offset = y*150 + x*3;
				final int rgb = (x < image.getWidth() && y < image.getHeight()) ? image.getRGB(x, y) : 0;
				input.putScalar(new int[] { index, offset }, (rgb >> 16) & 0xff);
				input.putScalar(new int[] { index, offset+1 }, (rgb >> 8) & 0xff);
				input.putScalar(new int[] { index, offset+2 }, rgb & 0xff);
			}));
			/* Dump tag data in output neurons */
			
			final Collection<String> imageTags = tags.get(id);
			IntStream.range(0, tagValues.size()).forEach(tagIndex -> 
					labels.putScalar(new int[]{ index, tagIndex}, imageTags.contains(tagValues.get(tagIndex)) ? 1 : 0));
		});
		
        // create dataset object
        DataSet ds = new DataSet(input, labels);

        // Set up network configuration
        NeuralNetConfiguration.Builder builder = new NeuralNetConfiguration.Builder();

        // Updater and learning rate
        builder.updater(new Sgd(0.1));
        // fixed seed for the random generator, so any run of this program
        // brings the same results - may not work if you do something like
        // ds.shuffle()
        builder.seed(123);
        // init the bias with 0 - empirical value, too
        builder.biasInit(0);
        
        // create a multilayer network
        ListBuilder listBuilder = builder.list();
        // Layer count includes the output layer, excludes the input layer
        
        int hiddenLayerCount = 4;
        double reductionFactor = Math.pow(((double)inputLayerSize) / outputLayerSize, 1.0 / (hiddenLayerCount + 1));
        
        double layerSize = inputLayerSize;

		for (int layerNumber = 0; layerNumber < hiddenLayerCount; layerNumber++) {
			final double nextLayerSize = (layerSize / reductionFactor);
			// build and set as layer
			listBuilder.layer(layerNumber, new DenseLayer.Builder()
					// input connections - simultaneously defines the number of input
					// neurons, because it's the first non-input-layer
					.nIn((int)layerSize)
					// number of outgoing connections, nOut simultaneously defines the
					// number of neurons in this layer
					.nOut((int)nextLayerSize)
					// put the output through the sigmoid function, to cap the output
					// value between 0 and 1
					.activation(Activation.SIGMOID)
					// random initialize weights with values between 0 and 1
					.weightInit(WeightInit.DISTRIBUTION)
					.dist(new UniformDistribution(0, 1)).build());
			layerSize = nextLayerSize;
		}

        // MCXENT or NEGATIVELOGLIKELIHOOD (both are mathematically equivalent) work ok for this example - this
        // function calculates the error-value (aka 'cost' or 'loss function value'), and quantifies the goodness
        // or badness of a prediction, in a differentiable way
        // For classification (with mutually exclusive classes), use multiclass cross entropy, in conjunction
		// with softmax activation function
		listBuilder.layer(hiddenLayerCount, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
				// must be the same amout as neurons in the layer before
				.nIn((int)layerSize)
				// neurons in this layer
				.nOut(outputLayerSize)
				.activation(Activation.SIGMOID)
				.weightInit(WeightInit.DISTRIBUTION)
				.dist(new UniformDistribution(0, 1)).build());
		
        // no pretrain phase for this network
        listBuilder.pretrain(false);

        // seems to be mandatory
        // according to agibsonccc: You typically only use that with
        // pretrain(true) when you want to do pretrain/finetune without changing
        // the previous layers finetuned weights that's for autoencoders and
        // rbms
        listBuilder.backprop(true);

        // build and init the network, will check if everything is configured
        // correct
        MultiLayerConfiguration conf = listBuilder.build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        // add an listener which outputs the error every 100 parameter updates
        net.setListeners(new ScoreIterationListener(100));

        // C&P from LSTMCharModellingExample
        // Print the number of parameters in the network (and for each layer)
        Layer[] layers = net.getLayers();
        int totalNumParams = 0;
        for (int i = 0; i < layers.length; i++) {
            int nParams = layers[i].numParams();
            System.out.println("Number of parameters in layer " + i + ": " + nParams);
            totalNumParams += nParams;
        }
        System.out.println("Total number of network parameters: " + totalNumParams);

        // here the actual learning takes place
        for( int i=0; i<100; i++ ) {
        	System.out.println(new Date() +": "+ i +"%");
        	for( int j=0; j<10; j++ ) {
        		net.fit(ds);
        	}
        }
        System.out.println(new Date() +": 100%");

        // create output for every training sample
        INDArray output = net.output(ds.getFeatureMatrix());
        System.out.println(output);

        // let Evaluation prints stats how often the right output had the
        // highest value
        Evaluation eval = new Evaluation(outputLayerSize);
        eval.eval(ds.getLabels(), output);
        System.out.println(eval.stats());
    }
    
    private static BufferedImage load(File file) {
    	try {
    		final BufferedImage image = ImageIO.read(file);
    		System.out.println(file + ": " + image.getWidth() + "×" + image.getHeight());
			return image;
    	} catch (IOException e) {
    		throw new RuntimeException(e);
    	}
    }
    
}
