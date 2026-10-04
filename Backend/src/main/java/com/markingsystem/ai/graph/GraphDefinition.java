package com.markingsystem.ai.graph;

import java.util.LinkedHashMap;
import java.util.Map;

public class GraphDefinition<T> {

    private final Map<String, GraphNode<T>> nodes = new LinkedHashMap<>();
    private final Map<String, String> linearEdges = new LinkedHashMap<>();
    private final Map<String, ConditionalEdge<T>> conditionalEdges = new LinkedHashMap<>();
    private String startNode;

    public GraphDefinition<T> startAt(String nodeName) {
        this.startNode = nodeName;
        return this;
    }

    public GraphDefinition<T> addNode(String nodeName, GraphNode<T> node) {
        nodes.put(nodeName, node);
        return this;
    }

    public GraphDefinition<T> addEdge(String from, String to) {
        linearEdges.put(from, to);
        return this;
    }

    public GraphDefinition<T> addConditionalEdges(String from, GraphRouter<T> router, Map<String, String> routes) {
        conditionalEdges.put(from, new ConditionalEdge<>(router, routes));
        return this;
    }

    public void run(T state) {
        String current = startNode;
        while (current != null) {
            GraphNode<T> node = nodes.get(current);
            if (node == null) {
                throw new IllegalStateException("graph node not found: " + current);
            }
            node.execute(state);

            ConditionalEdge<T> conditionalEdge = conditionalEdges.get(current);
            if (conditionalEdge != null) {
                String routeKey = conditionalEdge.router.route(state);
                current = conditionalEdge.routes.get(routeKey);
                continue;
            }

            current = linearEdges.get(current);
        }
    }

    private record ConditionalEdge<T>(GraphRouter<T> router, Map<String, String> routes) {
    }
}
