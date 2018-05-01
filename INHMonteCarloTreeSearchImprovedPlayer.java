import java.util.ArrayList;
import java.util.List;

import org.ggp.base.apps.player.Player;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

/**
 * Insert_Team_Name
 * Monte Carlo Tree Search Player
 * Searches entire move tree to play best move.
 *
 */
public class INHMonteCarloTreeSearchImprovedPlayer extends GGPlayer {

	class Node {
		public boolean expanded = false;  // Meant for checking whether we should expand on node
		public double utility = 0;
		public int visits = 0;


		public Move previousPlayerMove;  // Move made by our player previously (for max nodes)

		public Node parent;
		public List<Node> children;

		public MachineState state;  // Current state of game

		public boolean isMaxNode;  // True if max node, false if min node

		public Node(Node parent, Move previousPlayerMove, MachineState state, boolean isMaxNode) {
			this.previousPlayerMove = previousPlayerMove;

			this.parent = parent;
			this.children = new ArrayList<Node>();

			this.state = state;
			this.isMaxNode = isMaxNode;
		}
	}

	class MonteCarloTree {
		public Role playerRole;
		public StateMachine machine;
		public Node rootNode;

		public MonteCarloTree(Role playerRole, MachineState state, StateMachine machine) {
			this.rootNode = new Node(null, null, state, true);
			this.playerRole = playerRole;
			this.machine = machine;
		}

		/* Expands min nodes */
		private void expandMin(Node node, Move playerMove) throws MoveDefinitionException, TransitionDefinitionException {
			List<List<Move>> possibleJointMoves = findLegalJoints(this.playerRole, playerMove, node.state, this.machine);
			for (int i = 0; i < possibleJointMoves.size(); i++) {
				node.children.add(new Node(node, playerMove, findNext(possibleJointMoves.get(i), node.state, this.machine), true));
			}
		}

		/* Expands max nodes */
		private void expandMax(Node node) throws MoveDefinitionException, TransitionDefinitionException {
			List<Move> legalMoves = findLegals(this.playerRole, node.state, this.machine);
			for (int i = 0; i < legalMoves.size(); i++) {
				Node childNode = new Node(node, legalMoves.get(i), node.state, false);
				expandMin(childNode, legalMoves.get(i));
				node.children.add(childNode);
			}
		}

		private double selectFn(Node node) {
			double factor = (node.isMaxNode) ? -1.0 : 1.0;
			return factor * node.utility / node.visits + Math.sqrt(2 * Math.log(node.parent.visits) / node.visits);
		}

		private Node selectHelper(Node node)
				throws MoveDefinitionException, TransitionDefinitionException {
			if (node == null) {
				return null;
			}
			if (node.visits == 0) {
				return node;
			}
			for (int i = 0; i < node.children.size(); i++) {
				Node childNode = node.children.get(i);
				for (int j = 0; j < childNode.children.size(); j++) {
					Node grandChildNode = childNode.children.get(j);
					if (grandChildNode.visits == 0) {
						return grandChildNode;
					}
				}
			}

			/* Select child */
			double score = -1;
			Node firstResult = null;

			for (int i = 0; i < node.children.size(); i++) {
				Node childNode = node.children.get(i);
				double newScore = selectFn(childNode);
				if (!findTerminalp(childNode.state, this.machine) && (firstResult == null || newScore > score)) {
					score = newScore;
					firstResult = childNode;
				}
			}

			if (firstResult == null) {
				return null;
			}

			/* Select grand-child */
			score = -1;
			Node secondResult = null;

			for (int j = 0; j < firstResult.children.size(); j++) {
				Node grandChildNode = firstResult.children.get(j);
				double newScore = selectFn(grandChildNode);
				if (!findTerminalp(grandChildNode.state, this.machine) && (secondResult == null || newScore > score)) {
					score = newScore;
					secondResult = grandChildNode;
				}
			}

			return selectHelper(secondResult);
		}

		/**
		 *
		 * @param node
		 * @throws MoveDefinitionException
		 * @throws TransitionDefinitionException
		 *
		 * Takes a given node and expands it down to its grand-children.  Function assumes that the
		 * given node is a max node.
		 */
		public void expand(Node node)
				throws MoveDefinitionException, TransitionDefinitionException {
			if (node.expanded) {
				return;
			}
			expandMax(node);
			node.expanded = true;
		}

		public Node select() throws MoveDefinitionException, TransitionDefinitionException {
			return selectHelper(this.rootNode);
		}

		public double simulate(Node node, int count)
				throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
			double total = 0;
			for (int i = 0; i < count; i++) {
				total = total + depthCharge(this.playerRole, node.state, this.machine);
			}
			return total / count;
		}

		public void backpropagate(Node node, double score) {
			node.visits++;
			/*
			node.utility += score;
			if (node.parent != null) {
				backpropagate(node.parent, score);
			}
			*/
			/**/
			if (node.children.size() == 0) {
				node.utility = score;
			} else if (node.isMaxNode) {
				double utility = score;
				for (int i = 0; i < node.children.size(); i++) {
					double candidate = node.children.get(i).utility;
					if (candidate > utility) {
						utility = candidate;
					}
				}
				node.utility = utility;
			} else {
				double utility = score;
				for (int i = 0; i < node.children.size(); i++) {
					double candidate = node.children.get(i).utility;
					if (candidate < utility && node.children.get(i).visits > 0) {
						utility = candidate;
					}
				}
				node.utility = utility;
			}
			if (node.parent != null) {
				backpropagate(node.parent, node.utility);
			}
			/**/
		}

		public Move chooseCurrBestMove() {
			double bestScore = -1;
			Move bestMove = null;
			for (int i = 0; i < this.rootNode.children.size(); i++) {
				Node childNode = rootNode.children.get(i);
				if (childNode.visits > 0) {
					double score = childNode.utility / childNode.visits;
					System.out.println("Utility considered: " + childNode.utility);
					System.out.println("Move: " + childNode.previousPlayerMove);
					System.out.println("Visits: " + childNode.visits);
					System.out.println("Scaled utility considered: " + score);
					score = childNode.utility;
					if (score > bestScore) {
						bestScore = score;
						bestMove = childNode.previousPlayerMove;
					}
				}
			}
			return bestMove;
		}
	}

	private long TIME_LIMIT = 3000;
	private long currTimeout = 0;
	private int numSimulations = 25;

	private boolean doWeHaveTime() {
		return (currTimeout - System.currentTimeMillis()) > TIME_LIMIT;
	}

	/**
	 * Number of levels we are allowed to search.
	 */
	private int limit = 9;

	/**
	 * All we have to do here is call the Player's initialize method with
	 * our name as the argument so that the Player GUI knows which name to
	 * have selected at startup. This is all you need in the main method
	 * of your own player.
	 */
	public static void main(String[] args) {
		Player.initialize(new INHMonteCarloTreeSearchImprovedPlayer().getName());
	}

	/**
	 * Currently, we can get along just fine by using the Prover State Machine.
	 * We will implement a more optimized PropNet State Machine later. The Cached
	 * State Machine is a wrapper that reduces the number of calls to the Prover
	 * State Machine by returning results of method calls that have been made previously.
	 * (e.g. getNextState calls or getLegalMoves for the same combination of parameters)
	 */
	@Override
	public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new ProverStateMachine());
	}

	/**
	 * If we wanted to use the metagame (or start) clock to compute something
	 * about the game (or explore the game tree), we could do so here. Since
	 * this is just a legal player, there is no need for such computation.
	 */
	@Override
	public void start(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {

	}

	/**
	 * Where your player selects the move they want to play. In-line comments
	 * explain each line of code. Your goal essentially boils down to returning the best
	 * move possible.
	 *
	 * The current state for the player is updated between moves automatically for you.
	 *
	 * The value of the timeout variable is the UNIX time by which you need to submit your move.
	 * You can determine how much time your player has left (in milliseconds) by using the following line of code:
	 * long timeLeft = timeout - System.currentTimeMillis();
	 *
	 * Make sure to submit your move before this time runs out. It's also a good
	 * idea to leave a couple seconds (2-4) as buffer for network lag/spikes and
	 * so that you don't overrun your time thus timing out (which plays
	 * a random move for you and counts as an error -- two very bad things).
	 */
	@Override
	public Move play(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		//Gets our state machine (the same one as returned in getInitialStateMachine)
		//This State Machine simulates the game we are currently playing.
		StateMachine machine = getStateMachine();

		//Gets the current state we're in (e.g. move 2 of a game of tic tac toe where X just played in the center)
		MachineState state = getCurrentState();

		//Gets our role (e.g. X or O in a game of tic tac toe)
		Role role = getRole();

		//Gets all legal moves for our player in the current state
		List<Move> legalMoves = findLegals(role, state, machine);
		if (legalMoves.size() == 1) {
			System.out.println("I am playing: " + legalMoves.get(0));
			return legalMoves.get(0);
		}

		//Begin MCTS
		MonteCarloTree tree = new MonteCarloTree(role, state, machine);
		this.currTimeout = timeout;

		while (doWeHaveTime()) {
			Node selectedNode = tree.select();
			if (selectedNode == null) {
				break;
			}
			tree.expand(selectedNode);
			double score = tree.simulate(selectedNode, this.numSimulations);
			tree.backpropagate(selectedNode, score);
		}
		Move bestMove = tree.chooseCurrBestMove();
		if (bestMove == null) {
			bestMove = legalMoves.get(0);
		}

		System.out.println("I am playing: " + bestMove);
		return bestMove;
	}

	/**
	 * Can be used for cleanup at the end of a game, if it is needed.
	 */
	@Override
	public void stop() {

	}

	/**
	 * Can be used for cleanup in the event a game is aborted while
	 * still in progress, if it is needed.
	 */
	@Override
	public void abort() {

	}

	/**
	 * Returns the name of the player.
	 */
	@Override
	public String getName() {
		return "INHMonteCarloTreeSearchImproved_player";
	}



}
