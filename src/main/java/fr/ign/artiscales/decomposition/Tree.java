package fr.ign.artiscales.decomposition;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

public class Tree<T> {
  private List<Tree<T>> children = new LinkedList<Tree<T>>();
  private T data;


  public Tree(T data) {
    this.setData(data);
  }

  public Tree(T data, List<Tree<T>> children) {
    this(data);
    this.setChildren(children);
  }

  public List<Tree<T>> getChildren() {
    return children;
  }

  public void setChildren(List<Tree<T>> leaves) {
    this.children = leaves;
  }

  public T getData() {
    return data;
  }

  public void setData(T data) {
    this.data = data;
  }

  public Stream<T> stream() {
    if (children.isEmpty()) return Stream.of(data);
    return Stream.concat(Stream.of(data), children.stream().flatMap(c->c.childrenStream()));
  }

  public Stream<T> childrenStream() {
    if (children.isEmpty()) return Stream.of(data);
    return children.stream().flatMap(c->c.childrenStream());
  }
}