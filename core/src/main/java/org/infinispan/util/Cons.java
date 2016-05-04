package org.infinispan.util;

import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * Node in an immutable single-linked list.
 *
 * @author Dan Berindei
 * @since 9.0
 */
public class Cons<T> extends AbstractCollection<T> {
   private static final Cons EMPTY = new Cons<Object>(null, null);
   private final T head;
   private final Cons<T> tail;

   @SuppressWarnings("unchecked")
   public static <T> Cons<T> empty() {
      return EMPTY;
   }

   public static <T> Cons<T> make(T head, Cons<T> tail) {
      return new Cons<T>(Objects.requireNonNull(head), Objects.requireNonNull(tail));
   }

   private Cons(T head, Cons<T> tail) {
      this.head = head;
      this.tail = tail;
   }

   public T head() {
      return head;
   }

   public Cons<T> tail() {
      return tail;
   }

   @Override
   public boolean isEmpty() {
      return tail == null;
   }

   @Override
   public Iterator<T> iterator() {
      return new Iterator<T>() {
         Cons<T> current = Cons.this;

         @Override
         public boolean hasNext() {
            return !current.isEmpty();
         }

         @Override
         public T next() {
            if (current.isEmpty())
               throw new NoSuchElementException();

            T value = current.head;
            current = current.tail;
            return value;
         }
      };
   }

   @Override
   public int size() {
      int size = 0;
      Cons<T> current = this;
      while (current != EMPTY) {
         size++;
         current = current.tail;
      }
      return size;
   }

   @Override
   public void forEach(Consumer<? super T> action) {
      for (Cons<T> current = this; current != EMPTY; current = current.tail) {
         action.accept(current.head);
      }
   }

   @Override
   public Spliterator<T> spliterator() {
      return new Spliterator<T>() {
         Cons<T> current = Cons.this;

         @Override
         public boolean tryAdvance(Consumer<? super T> action) {
            Objects.requireNonNull(action);
            if (current == EMPTY)
               return false;

            action.accept(current.head);
            current = current.tail;
            return true;
         }

         @Override
         public Spliterator<T> trySplit() {
            return null;
         }

         @Override
         public long estimateSize() {
            return Long.MAX_VALUE;
         }

         @Override
         public int characteristics() {
            return ORDERED | IMMUTABLE;
         }
      };
   }
}
