/**************Ant Colony Optimization using Spark*****************
 * Author: Afrooz Rahmati
 * Group No: 6
 * December 2020
 */

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaSparkContext;
import java.util.Arrays;
import scala.Tuple2;
import java.util.ArrayList;
import java.io.Serializable;
import java.security.PublicKey;
import java.util.List;
import java.util.Iterator;
import java.lang.Math;
import java.util.OptionalInt;
import java.util.Random;
import java.util.stream.IntStream;


//Ant class
class Ant implements Serializable  {

    //parameters to keep ant trails and track the list of visited cities
	protected int trailSize;
	protected int trail[];
    protected boolean visited[];
    protected double trails[][];
    private static double evaporation = 0.5;
    private static double c = 1.0;

    //class constructor, initializing the parameters
	public Ant(int tourSize) {
		this.trailSize = tourSize;
		this.trail = new int[tourSize];
        this.visited = new boolean[tourSize];
        this.trails = new double[tourSize][tourSize];
	}

    //tracking visited city by ant 
	protected void visitCity(int currentIndex , int city) {
		trail[currentIndex + 1] = city;
		visited[city] = true;
	}

    //check whether the city is visited before by ant
	protected boolean visited(int i) {
		return visited[i];
	}

    //calculating the lenght of the tour visited by ant
	protected double trailLength(double graph[][]) {
		double length = graph[trail[trailSize - 1]][trail[0]];
		for (int i = 0; i < trailSize - 1; i++) 
			length += graph[trail[i]][trail[i + 1]];
		
		return length;
	}

    //initializing the trail 
	protected void clear() {
		for (int i = 0; i < trailSize; i++)
			visited[i] = false;
    }

    public void UpdateTrails( int numberOfCities  )
    {
        for (int i = 0; i < numberOfCities; i++) {
            for (int j = 0; j < numberOfCities; j++) {
                trails[i][j] *= evaporation;
            }
        }
    }

    public void clearTrails(int numberOfCities)
    {
        /////////////clear Trails////////////////////////
        IntStream.range(0, numberOfCities)
        .forEach(i -> {
            IntStream.range(0, numberOfCities)
                .forEach(j -> trails[i][j] = c);
        });
    }


}

/***
 * ******************Ant Colony Optimization class *******************************
 * Implementation Strategy:
 * (1) Data parallelism. Use parallelize() of Spark to convert the array to a distributed dataset
 * then use this dataset as the input of map() to start m number of Map tasks. 
 * (2) Map Tasks. Because all Map tasks will use the same algorithm, 
 * we only need to complete the process that one ant constructs a path. 
 * Construct path according to sequential code, Then output the walking distance and path of each ant.
 * The calculations of the algorithm are mainly concentrated in Map stage. 
 * The outputs of map() are terms of key-value pairs, in which the distance is key and the path is value.
 * Sort the distances using sortByKey().
 * (3)Reduce Tasks. Use take(1) to find the shortest distance and its corresponding path.
 * (4)Iteration: Update the pheromone matrix according to the results of Reduce tasks, 
 * and then save the current minimum distance and optimal path. Iterate Map tasks and Reduce tasks 
 * to obtain the final result.
 */

public class ACO {

    //c indicates the original number of trails, at the start of the simulation. 
    private static double c = 1.0;
    //alpha controls the pheromone importance 
    private static double alpha = 1;
    //beta controls the distance priority
    private static double beta = 5;
    //the evaporation variable shows the percent how much the pheromone is evaporating in every iteration
    private static double evaporation = 0.5;
    //Q provides information about the total amount of pheromone left on the trail by each Ant
    private static double Q = 500;
    //how many ants we'll use per city
    private static double antFactor = 0.8;
    //controling the randomness of our simulation
    private static double randomFactor = 0.01;
    //the number of iterations
    private static int maxIterations = 100;


    /**
     * Generate initial solution ....randomly create input data for our simulation
     */
    public static double[][] generateRandomMatrix(int n) {
        Random random = new Random();
        double[][] randomMatrix = new double[n][n];
        IntStream.range(0, n)
            .forEach(i -> IntStream.range(0, n)
                .forEach(j -> randomMatrix[i][j] = Math.abs(random.nextInt(100) + 1)));
        return randomMatrix;
    }


    public static void main(String[] args) {
        
        int numberOfCities;
        int numberOfAnts;
        //contain city distances
        double graph[][];
        double trails[][];
        List<Ant> ants = new ArrayList<>();
        Random random = new Random();
        //keeping the probabilities to select the next city
        double probabilities[];
        //The shortest path
        int[] bestTourOrder;
        //The lenght of shortest path
        double bestTourLength;

        int noOfCities = Integer.parseInt(args[0]) ;

        ///////initializing/////////////////
        graph = generateRandomMatrix(noOfCities);
        numberOfCities = graph.length;
        //number of ants require to find the shortest path
        numberOfAnts = (int) (numberOfCities * antFactor);
        trails = new double[numberOfCities][numberOfCities];
        probabilities = new double[numberOfCities];
        IntStream.range(0, numberOfAnts)
            .forEach(i -> ants.add(new Ant(numberOfCities)));

        ////////////////setup Ants////////
        IntStream.range(0, numberOfAnts)
        .forEach(i -> {
            ants.forEach(ant -> {
                ant.clear();
                ant.visitCity( -1 , random.nextInt(numberOfCities));
                //ant.clearTrails(numberOfCities) ;
            });
        });

        // /////////////clear Trails////////////////////////
        IntStream.range(0, numberOfCities)
        .forEach(i -> {
            IntStream.range(0, numberOfCities)
                .forEach(j -> trails[i][j] = c);
        });
    
    

        //spark configuration
        SparkConf conf = new SparkConf( ).setAppName( "Ant Colony Optimization Spark Implementation" );
        JavaSparkContext jsc = new JavaSparkContext( conf );

        // now start a timer
        long startTime = System.currentTimeMillis();

        //parallelize() to convert ant array to a distributed dataset
        JavaRDD<Ant> antRDD= jsc.parallelize(ants);  

        //initializing the best path and it's lenght with first ant value
        bestTourOrder = ants.get(0).trail;
        bestTourLength = ants.get(0).trailLength(graph);

        
        //Iterate Map tasks and Reduce tasks to obtain the final result
        for (int iteration= 0 ; iteration < maxIterations ;iteration++){
           //Map task: we only need to complete the process that one ant constructs a path
            JavaPairRDD <Double, Tuple2< int[] , double[][]>> network = antRDD.mapToPair (  ant -> {                              
                //for each city           
                for ( int index = 0;  index< numberOfCities - 1 ; index++ ) {

                    //////////////select Next City////////////////                                 
                    int t = random.nextInt(numberOfCities - index);
                    int nextCity = -1; 
                    if (random.nextDouble() < randomFactor) {
                        OptionalInt cityIndex = IntStream.range(0, numberOfCities)
                            .filter(i -> i == t && !ant.visited(i))
                            .findFirst();
                        if (cityIndex.isPresent()) {
                            nextCity = cityIndex.getAsInt();
                        }
                    }
                    
                    //calculate Probabilities: calculate probabilities to select the next city,
                    //remembering that ants prefer to follow stronger and shorter trails
                    int i = ant.trail[index];
                    double pheromone = 0.0;
                    for (int l = 0; l < numberOfCities; l++) {
                        if (!ant.visited(l)) {
                            pheromone += Math.pow(trails[i][l], alpha) * Math.pow(1.0 / graph[i][l], beta);
                        }
                    }
                    for (int j = 0; j < numberOfCities; j++) {
                        if (ant.visited(j)) {
                            probabilities[j] = 0.0;
                        } else {
                            double numerator = Math.pow(trails[i][j], alpha) * Math.pow(1.0 / graph[i][j], beta);
                            probabilities[j] = numerator / pheromone;
                        }
                    }                   
                    double r = random.nextDouble();
                    for (int k = 0; k < numberOfCities; k++) {
                        if ( probabilities[k] >= r & !ant.visited(k)) {
                                nextCity = k;
                        }
                    }

                    // for those ants whic they didn't choose the stronger path
                    if (nextCity==-1){
                        OptionalInt cityIndex = IntStream.range(0, numberOfCities)
                        .filter(h -> !ant.visited(h))
                        .findFirst();
                        if (cityIndex.isPresent()) 
                            nextCity = cityIndex.getAsInt();   
                    }
                    //finaly ant visit the next selected city
                    ant.visitCity( index , nextCity);

                    //compute contribuation of each ant and update the city's trail
                    double contribution = Q / ant.trailLength(graph);
                    for (int city = 0; city < numberOfCities - 1; city++) {
                        trails[ant.trail[city]][ant.trail[city + 1]] += contribution;
                    }
                    trails[ant.trail[numberOfCities - 1]][ant.trail[0]] += contribution;                
                
                } //end for

                //return pairRDD , the key is the shortest distance and path is the value

                Tuple2< int[] , double[][]> tarilInfo = new Tuple2<>(ant.trail ,trails );
                return new Tuple2<>( ant.trailLength(graph) , tarilInfo );

            });//end map

            //sorting the distnaces ( shortest first )
            network = network.sortByKey();

            //this is also working , no difference 
            Tuple2<Double, Tuple2< int[] , double[][]>> a = network.take(1).get(0);
            
            if (a._1 < bestTourLength) {
                bestTourLength = a._1;
                bestTourOrder = a._2._1;
            }
              

         //   System.err.println("*************************iteration : "  + iteration + " *******************************" );

            //update Trails for the next iteration
            for (int i = 0; i < numberOfCities; i++) {
                for (int j = 0; j < numberOfCities; j++) {
                    trails[i][j] = a._2._2[i][j];
                    trails[i][j] *= evaporation;
                }
               // System.err.println(" trails i: " + i + " " + Arrays.toString(trails[i]) );
            }

            // for( Ant anto : ants ) {
            //     //the shortest path to destination                 
            //         System.err.println(" distance " + anto.trailLength(graph) + "tour" + Arrays.toString(anto.trail) );
            // } 

            // printing the shortest path lenght found for debugging 
           // System.err.println("iteration : "  + iteration + " bestTourLength = " + bestTourLength );
           // System.err.println("tour = " +Arrays.toString( bestTourOrder ) );
            //update ants value and reset the ants for the next iteartion
            // //update Trails for the next iteration

            
            antRDD = antRDD.map ( ant -> {
                ant.clear();
                ant.visitCity( -1 , random.nextInt(numberOfCities));
                return ant;
             });

        } //end iteration


    //return the final result    
    System.err.println("Shortest distance lenght: " + bestTourLength + " and path = " + Arrays.toString(bestTourOrder) );
    
    // stopping the timer.
    long endTime = System.currentTimeMillis();
    System.err.println("Execution time (in mili seconds) " + (endTime - startTime));

    jsc.stop( );

    }//end main
    
}
