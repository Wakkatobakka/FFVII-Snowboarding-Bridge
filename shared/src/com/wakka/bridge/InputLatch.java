package com.wakka.bridge;

import java.util.HashMap;
import java.util.Map;

/** Contacts own their masks. Releasing one cannot release another contact's key. */
public final class InputLatch {
    private final Map<Integer,Integer> contacts=new HashMap<>();
    public synchronized int set(int contact,int mask) {
        if(mask==0) contacts.remove(contact); else contacts.put(contact,mask);
        return mask();
    }
    public synchronized int remove(int contact) { contacts.remove(contact); return mask(); }
    public synchronized int clear() { contacts.clear(); return 0; }
    public synchronized int mask() { int m=0; for(int v:contacts.values()) m|=v; return m; }
    public synchronized int contacts() { return contacts.size(); }
}
