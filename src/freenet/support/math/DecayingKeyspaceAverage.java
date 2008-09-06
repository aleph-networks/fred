/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.support.math;

import freenet.node.Location;

import freenet.support.SimpleFieldSet;

/**
 * @author robert
 *
 * A filter on BootstrappingDecayingRunningAverage which makes it aware of the circular keyspace.
 */
public class DecayingKeyspaceAverage implements RunningAverage {

	private static final long serialVersionUID = 5129429614949179428L;
	/**
	'avg' is the normalized average location, note that the the reporting bounds are (-2.0, 2.0) however.
	 */
	BootstrappingDecayingRunningAverage avg;

	public DecayingKeyspaceAverage(double defaultValue, int maxReports, SimpleFieldSet fs) {
		avg = new BootstrappingDecayingRunningAverage(defaultValue, -2.0, 2.0, maxReports, fs);
	}

	public DecayingKeyspaceAverage(BootstrappingDecayingRunningAverage a) {
		//check the max/min values? ignore them?
		avg = (BootstrappingDecayingRunningAverage) a.clone();
	}

	@Override
	public synchronized Object clone() {
		return new DecayingKeyspaceAverage(avg);
	}

	public synchronized double currentValue() {
		return avg.currentValue();
	}

	public synchronized void report(double d) {
		if((d < 0.0) || (d > 1.0))
			//Just because we use non-normalized locations doesn't mean we can accept them.
			throw new IllegalArgumentException("Not a valid normalized key: " + d);
		double superValue = avg.currentValue();
		double thisValue = Location.normalize(superValue);
		double diff = Location.change(thisValue, d);
		double toAverage = (superValue + diff);
		/*
		To gracefully wrap around the 1.0/0.0 threshold we average over (or under) it, and simply normalize the result when reporting a currentValue
		---example---
		d=0.2;          //being reported
		superValue=1.9; //already wrapped once, but at 0.9
		thisValue=0.9;  //the normalized value of where we are in the keyspace
		diff = +0.3;    //the diff from the normalized values; Location.change(0.9, 0.2);
		avg.report(2.2);//to successfully move the average towards the closest route to the given value.
		 */
		avg.report(toAverage);
		double newValue = avg.currentValue();
		if(newValue < 0.0 || newValue > 1.0)
			avg.setCurrentValue(Location.normalize(newValue));
	}

	public synchronized double valueIfReported(double d) {
		if((d < 0.0) || (d > 1.0))
			throw new IllegalArgumentException("Not a valid normalized key: " + d);
		double superValue = avg.currentValue();
		double thisValue = Location.normalize(superValue);
		double diff = Location.change(thisValue, d);
		return Location.normalize(avg.valueIfReported(superValue + diff));
	}

	public synchronized long countReports() {
		return avg.countReports();
	}

	public void report(long d) {
		throw new IllegalArgumentException("KeyspaceAverage does not like longs");
	}

	public synchronized void changeMaxReports(int maxReports) {
		avg.changeMaxReports(maxReports);
	}

	public synchronized SimpleFieldSet exportFieldSet(boolean shortLived) {
		return avg.exportFieldSet(shortLived);
	}

	///@todo: make this a junit test
	public static void main(String[] args) {
		DecayingKeyspaceAverage a = new DecayingKeyspaceAverage(0.9, 10, null);
		a.report(0.9);
		for(int i = 10; i != 0; i--) {
			a.report(0.2);
			System.out.println("<-0.2-- current=" + a.currentValue());
		}
		for(int i = 10; i != 0; i--) {
			a.report(0.8);
			System.out.println("--0.8-> current=" + a.currentValue());
		}
		System.out.println("--- positive wrap test ---");
		for(int wrap = 3; wrap != 0; wrap--) {
			System.out.println("wrap test #" + wrap);
			for(int i = 10; i != 0; i--) {
				a.report(0.25);
				System.out.println("<-0.25- current=" + a.currentValue());
			}
			for(int i = 10; i != 0; i--) {
				a.report(0.5);
				System.out.println("--0.5-> current=" + a.currentValue());
			}
			for(int i = 10; i != 0; i--) {
				a.report(0.75);
				System.out.println("-0.75-> current=" + a.currentValue());
			}
			for(int i = 10; i != 0; i--) {
				a.report(1.0);
				System.out.println("<-1.0-- current=" + a.currentValue());
			}
		}
		System.out.println("--- negative wrap test ---");
		a = new DecayingKeyspaceAverage(0.2, 10, null);
		a.report(0.2);
		for(int wrap = 3; wrap != 0; wrap--) {
			System.out.println("negwrap test #" + wrap);
			for(int i = 10; i != 0; i--) {
				a.report(0.75);
				System.out.println("-0.75-> current=" + a.currentValue());
			}
			for(int i = 10; i != 0; i--) {
				a.report(0.5);
				System.out.println("<-0.5-- current=" + a.currentValue());
			}
			for(int i = 10; i != 0; i--) {
				a.report(0.25);
				System.out.println("<-0.25- current=" + a.currentValue());
			}
			for(int i = 10; i != 0; i--) {
				a.report(1.0);
				System.out.println("--1.0-> current=" + a.currentValue());
			}
		}
	}
}
