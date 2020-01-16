package beast.evolution.operators;


import org.apache.commons.math.distribution.NormalDistributionImpl;

import beast.core.BEASTObject;
import beast.core.Description;
import beast.core.Input;
import beast.util.Randomizer;

public interface KernelDistribution {
	public static Input<Integer> defaultInitialInput = new Input<>("defaultInitial", "Number of proposals skipped before learning about the val" , 500);
	public static Input<Integer> defaultBurninInput = new Input<>("defaultBurnin", "Number of proposals skipped before any learned informatin is applied" , 500);

	/**
	 * @param m determines shape of Bactrian distribution. m=0.95 is recommended
	 * @param scaleFactor determines range of scale values, larger is bigger random scale changes
	 * @return random scale factor for scaling parameters
	 */
	public double getScaler(double value, double scaleFactor);
	default public double getScaler(double windowSize) {
		return getScaler(Double.NaN, windowSize);
	}

	/**
	 * @param m determines shape of Bactrian distribution. m=0.95 is recommended
	 * @param windowSize determines range of random delta values, larger is bigger random updates
	 * @return random delta value for random walks
	 */
	public double getRandomDelta(double value, double windowSize);
	default public double getRandomDelta(double windowSize) {
		return getRandomDelta(Double.NaN, windowSize);
	}
		
	static KernelDistribution newDefaultKernelDistribution() {
//		Bactrian kdist = new Bactrian();
//		return kdist;
		MirrorDistribution kdist = new MirrorDistribution();
		kdist.initAndValidate();
		return kdist;
	}
	

	@Description("Kernel distribution with two modes, so called Bactrian distribution")
	public class Bactrian extends BEASTObject implements KernelDistribution {
		public enum mode {normal, uniform, bactrian_normal, bactrian_uniform, bactrian_triangle, bactrian_airplane, bactrian_strawhat, bactrian_laplace};
		final public Input<mode> modeInput = new Input<>("mode", "", mode.bactrian_normal, mode.values());
		
	    final public Input<Double> windowSizeInput = new Input<>("m", "standard deviation for Bactrian distribution. "
	    		+ "Larger values give more peaked distributions. "
	    		+ "The default 0.95 is claimed to be a good choice (Yang 2014, book p.224).", 0.95);
	    
	    double m = 0.95;
	    
	    public mode kernelmode;
	    
		@Override
		public void initAndValidate() {
			m = windowSizeInput.get();
	        if (m <=0 || m >= 1) {
	        	throw new IllegalArgumentException("m should be withing the (0,1) range");
	        }
	        kernelmode = modeInput.get();
		}

		private double getRandomNumber() {
			switch (kernelmode) {
			case normal:
				return Randomizer.nextGaussian();
			case uniform:
				return Randomizer.nextDouble() - 0.5;
			default:
		        if (Randomizer.nextBoolean()) {
		        	return m + getBactrianRandomNumber();
		        } else {
		        	return -m + getBactrianRandomNumber();
		        }
			}
		}
		
		private double getBactrianRandomNumber() {
			switch (kernelmode) {
			case bactrian_normal:
				return Randomizer.nextGaussian() * Math.sqrt(1-m*m);
			case bactrian_laplace:
			{
				double u = Randomizer.nextDouble();
				if (u < 0.5) {
					return m * (Math.log(2 * u) / Math.sqrt(2));
				} else {
					return -m * (Math.log(2 * (1-u)) / Math.sqrt(2));
				}
			}
			case bactrian_triangle:
			{
				double u = Randomizer.nextDouble();
				if (u < 0.5) {
					return m * (-Math.sqrt(6) + 2 * Math.sqrt(3 * u));
				} else {
					return m * (Math.sqrt(6) - 2 * Math.sqrt(3 * (1-u)));
				}
			}
			case bactrian_uniform:
				return (Randomizer.nextDouble() - 0.5) * Math.sqrt(3);
			case bactrian_airplane:
				// TODO
			case bactrian_strawhat:
				// TODO
			default:
				return Double.NaN;
			}
		}
		
		public double getScaler(double oldValue, double scaleFactor) {
	        double scale = 0;
	        scale = scaleFactor * getRandomNumber();
	        scale = Math.exp(scale);
			return scale;
		}
		
		public double getRandomDelta(double oldValue, double windowSize) {
	        double value;
	        value = windowSize * getRandomNumber();
	        return value;
		}	
		
	}
	
	@Description("Distribution that learns mean m and variance s from the values provided."
			+ "Uses Bactrian kernel while in the process of learning")
	public class MirrorDistribution extends Bactrian {
		final public Input<Integer> initialInput = new Input<>("initial", "Number of proposals before m and s are considered in proposal. "
				+ "Must be larger than burnin, if specified. "
				+ "If not specified (or < 0), the operator uses " + defaultInitialInput.get(), -1); 
		final public Input<Integer> burninInput = new Input<>("burnin", "Number of proposals that are ignored before m and s are being updated. "
				+ "If initial is not specified, uses half the default initial value (which equals " + defaultBurninInput.get() + ")", 0);
		
		int callcount;
		int initial, burnin;
		double estimatedMean, estimatedSD;

		@Override
		public void initAndValidate() {
			callcount = 0;
			initial = initialInput.get();
			if (initial < 0) {
				initial = defaultInitialInput.get();
			}
			burnin = burninInput.get();
			if (burnin <= 0) {
				burnin = defaultBurninInput.get();
			}
			estimatedMean = 0; estimatedSD = 0;	
		}
				
		@Override
		public double getScaler(double value, double scaleFactor) {
			callcount++;
			double logValue = Math.log(value);
			if (callcount > initial) {
				double prevMean = estimatedMean;
				double n = callcount - initial;
				estimatedMean = logValue / n + prevMean * (n - 1.0) / n;
				
			    double ssq = estimatedSD * estimatedSD;
			    estimatedSD = Math.sqrt(ssq + (((logValue - prevMean) * (logValue - estimatedMean)) - ssq) / n);
			}
			if (Double.isNaN(value) || callcount < initial + burnin) {
				return super.getScaler(value, scaleFactor);
			}
			
			double delta = scaleFactor * Randomizer.nextGaussian() * estimatedSD;
			
			double mean = 2 * estimatedMean - value;
			double newValue = mean + delta;
			double mean2 = 2 * estimatedMean - newValue;
			double scale = -logValue + newValue;
//			logHR = //- logDensity(mean, scaleFactor * estimatedSD, newValue) // these terms cancel each other 
//					//+ logDensity(mean2, scaleFactor * estimatedSD, value)
//					+ scale;
			
			return Math.exp(scale);
		}
		
		@Override
		public double getRandomDelta(double value, double windowSize) {
			callcount++;
			if (callcount > initial) {
				double prevMean = estimatedMean;
				double n = callcount - initial;
				estimatedMean = value / n + prevMean * (n - 1.0) / n;
				
			    double ssq = estimatedSD * estimatedSD;
			    estimatedSD = Math.sqrt(ssq + ((value - prevMean) * (value - estimatedMean) - ssq) / n);
			}
			if (Double.isNaN(value) || callcount < initial + burnin) {
				return super.getRandomDelta(value, windowSize);
			}
			
			double delta = windowSize * Randomizer.nextGaussian() * estimatedSD;
			
			double mean = 2 * estimatedMean - value;
			double newValue = mean + delta;
			//double mean2 = 2 * estimatedMean - newValue;
			//logHR = + logDensity(mean, windowSize * estimatedSD, newValue) // these terms cancel each other
			//		- logDensity(mean2, windowSize * estimatedSD, value);
			//logHR = 0;
			
			return newValue - value;
		} 
		
		private double logDensity(double mean, double sigma, double x) {
			return (new NormalDistributionImpl(mean, sigma)).logDensity(x);
//	        double a = 1.0 / (Math.sqrt(2.0 * Math.PI) * sigma);
//	        double b = -(x - mean) * (x - mean) / (2.0 * sigma * sigma);
//	        return Math.log(a) + b;
		}

		
	}
	
}
