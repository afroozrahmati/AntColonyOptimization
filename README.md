# AntColonyOptimization
Ant Colony Optimization Summary
Ant Colony Optimization is a genetic algorithm that attempts to approximate a solution to the Traveling Salesman Problem by simulating the pathfinding performed by an ant colony. Each ant leaves behind a pheromone trail that increases the probability that the next ant will follow that trail, and these trails degrade over time. This algorithm uses a number of variables to tweak the output, for which the best values are chosen heuristically.
As a result, we'll achieve the shortest path between all nodes. 
 

Our program is a synchronous implementation obtained from www.baeldung.com, which makes three attempts at approximating a solution, with each attempt performing 1000 iterations of the algorithm, over a randomly generated graph of 21 cities. Our alterations allow us to change the number of iterations and the number of cities.
To ensure consistency in execution, our implementations have been locked to either use a pre-generated file or a random number generator seed of 256 for creating the graph.

Spark Implementation with Java
(1) 	Data parallelism. At first, initialize the city distance matrix and the pheromone matrix. Use parallelize() of Spark to convert the array of Ants to a distributed dataset, then use this dataset as the input of map() to start m Map tasks. 
(2) 	Map Tasks. Because all Map tasks will use the same algorithm, we only need to complete the process that one ant constructs a path. Construct path according to sequential code. The outputs of map() are terms of key-value pairs, in which the distance is key and the path is value. Sort the distances using sortByKey().
(3) 	Reduce Tasks. Use take(1) to find the shortest distance and its corresponding path.
(4) 	Iteration: Update the pheromone matrix according to the results of Reduce tasks, and then save the current minimum distance and optimal path. Iterate Map tasks and Reduce tasks to obtain the final result.
