//
// "This sample program is provided AS IS and may be used, executed, copied and modified without royalty payment by customer (a) for its own
// instruction and study, (b) in order to develop applications designed to run with an IBM WebSphere product, either for customer's own internal use 
// or for redistribution by customer, as part of such an application, in customer's own products. " 
//
// (C) COPYRIGHT International Business Machines Corp., 2005
// All Rights Reserved * Licensed Materials - Property of IBM
//

package com.ibm.samples.trade.direct;

import java.util.Collection;
import java.util.Iterator;
import java.util.HashMap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.ibm.samples.trade.*;
import com.ibm.samples.trade.util.*;

public class KeySequenceDirect {

	private static HashMap keyMap = new HashMap();

    public static synchronized Integer getNextID(Connection conn, String keyName, boolean inGlobalTxn)
	throws Exception
    {
		Integer nextID = null;
		// First verify we have allocated a block of keys 
		// for this key name
		// Then verify the allocated block has not been depleted
		// allocate a new block if necessary
		if ( keyMap.containsKey(keyName) == false)
			allocNewBlock(conn, keyName, inGlobalTxn);
		Collection block = 	(Collection) keyMap.get(keyName);
		
		Iterator ids = block.iterator();
		if ( ids.hasNext() == false )
			ids = allocNewBlock(conn, keyName, inGlobalTxn).iterator();
		//get and return a new unique key
		nextID = (Integer) ids.next();

		if (Log.doTrace()) Log.trace("KeySequenceDirect:getNextID - return new PK ID for Entity type: " + keyName + " ID=" + nextID);
		return nextID;
	}

	private static Collection allocNewBlock(Connection conn, String keyName, boolean inGlobalTxn)  
	throws Exception
	{
		try 
		{
			if (inGlobalTxn == false) conn.commit();  // commit any pending txns
			PreparedStatement stmt = conn.prepareStatement(getKeyForUpdateSQL);
			stmt.setString(1, keyName);
			ResultSet rs = stmt.executeQuery();
			if (!rs.next()) 
			{
				// No keys found for this name - create a new one
				PreparedStatement stmt2 = conn.prepareStatement(createKeySQL);
				int keyVal = 0;
				stmt2.setString(1, keyName);
				stmt2.setInt(2, keyVal);				
				int rowCount = stmt2.executeUpdate();
				stmt2.close();
				stmt.close();
				stmt = conn.prepareStatement(getKeyForUpdateSQL);				
				stmt.setString(1, keyName);				
				rs = stmt.executeQuery();
				rs.next();		
			}
			
			int keyVal = rs.getInt("keyval");
			
			stmt.close();
			
			stmt = conn.prepareStatement(updateKeyValueSQL);
			stmt.setInt(1, keyVal+TradeConfig.KEYBLOCKSIZE);
			stmt.setString(2, keyName);			
			int rowCount = stmt.executeUpdate();
			stmt.close();

			Collection block = new KeyBlock(keyVal, keyVal+TradeConfig.KEYBLOCKSIZE-1);
			keyMap.put(keyName, block);
			if (inGlobalTxn == false) conn.commit();
			return block;			
		}
		catch (Exception e)
		{
			String error = "KeySequenceDirect:allocNewBlock - failure to allocate new block of keys for Entity type: "+ keyName;
			Log.error(e, error);
			throw new Exception(error + e.toString());
		}
	}
	
	private static final String getKeyForUpdateSQL  = 
		"select * from keygenejb kg where kg.keyname = ?  for update";
	
	private static final String createKeySQL =
		"insert into keygenejb " +
		"( keyname, keyval ) " +
		"VALUES (  ?  ,  ? )";
		
	private static final String updateKeyValueSQL = 
		"update keygenejb set keyval = ? " +
		"where keyname = ?";
		
}

