package com.example.performance_optimize.speed;

import java.util.HashMap;
import java.util.Map;

/**
 * Chapter 5.5.2: O(1) Least Frequently Used (LFU) Cache Implementation.
 * Uses a primary key-to-node map paired with frequency-bucketed doubly linked lists
 * to guarantee strict O(1) time complexity for both get() and put() operations.
 */
public class LFUCache<K, V> {

    private static final class Node<K, V> {
        final K key;
        V value;
        int freq;
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
            this.freq = 1;
        }
    }

    private static final class DoublyLinkedList<K, V> {
        private final Node<K, V> head;
        private final Node<K, V> tail;
        int size;

        DoublyLinkedList() {
            head = new Node<>(null, null);
            tail = new Node<>(null, null);
            head.next = tail;
            tail.prev = head;
            size = 0;
        }

        void addFirst(Node<K, V> node) {
            node.next = head.next;
            node.prev = head;
            head.next.prev = node;
            head.next = node;
            size++;
        }

        void remove(Node<K, V> node) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
            node.prev = null;
            node.next = null;
            size--;
        }

        Node<K, V> removeLast() {
            if (size == 0) {
                return null;
            }
            Node<K, V> last = tail.prev;
            remove(last);
            return last;
        }
    }

    private final int capacity;
    private int minFreq;
    private final Map<K, Node<K, V>> keyMap = new HashMap<>();
    private final Map<Integer, DoublyLinkedList<K, V>> freqMap = new HashMap<>();

    public LFUCache(int capacity) {
        this.capacity = Math.max(capacity, 0);
        this.minFreq = 0;
    }

    public synchronized V get(K key) {
        if (capacity == 0 || !keyMap.containsKey(key)) {
            return null;
        }
        Node<K, V> node = keyMap.get(key);
        incrementFrequency(node);
        return node.value;
    }

    public synchronized void put(K key, V value) {
        if (capacity == 0) {
            return;
        }
        if (keyMap.containsKey(key)) {
            Node<K, V> node = keyMap.get(key);
            node.value = value;
            incrementFrequency(node);
            return;
        }
        if (keyMap.size() >= capacity) {
            DoublyLinkedList<K, V> minFreqList = freqMap.get(minFreq);
            if (minFreqList != null) {
                Node<K, V> evicted = minFreqList.removeLast();
                if (evicted != null) {
                    keyMap.remove(evicted.key);
                }
            }
        }
        Node<K, V> newNode = new Node<>(key, value);
        keyMap.put(key, newNode);
        minFreq = 1;
        freqMap.computeIfAbsent(1, k -> new DoublyLinkedList<>()).addFirst(newNode);
    }

    public synchronized int size() {
        return keyMap.size();
    }

    private void incrementFrequency(Node<K, V> node) {
        int currentFreq = node.freq;
        DoublyLinkedList<K, V> currentList = freqMap.get(currentFreq);
        if (currentList != null) {
            currentList.remove(node);
            if (currentFreq == minFreq && currentList.size == 0) {
                minFreq++;
            }
        }
        node.freq++;
        freqMap.computeIfAbsent(node.freq, k -> new DoublyLinkedList<>()).addFirst(node);
    }
}
