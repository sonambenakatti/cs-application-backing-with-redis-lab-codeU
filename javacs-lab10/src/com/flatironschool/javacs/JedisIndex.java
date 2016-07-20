package com.flatironschool.javacs;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.jsoup.select.Elements;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

/**
 * Represents a Redis-backed web search index.
 * 
 */
public class JedisIndex {

	private Jedis jedis;

	/**
	 * Constructor.
	 * 
	 * @param jedis
	 */
	public JedisIndex(Jedis jedis) {
		this.jedis = jedis;
	}
	
	/**
	 * Returns the Redis key for a given search term.
	 * 
	 * @return Redis key.
	 */
	private String urlSetKey(String term) {
		return "URLSet:" + term;
	}
	
	/**
	 * Returns the Redis key for a URL's TermCounter.
	 * 
	 * @return Redis key.
	 */
	private String termCounterKey(String url) {
		return "TermCounter:" + url;
	}

	/**
	 * Checks whether we have a TermCounter for a given URL.
	 * 
	 * @param url
	 * @return
	 */
	public boolean isIndexed(String url) {
		String redisKey = termCounterKey(url);
		return jedis.exists(redisKey);
	}
    
     /**
     * Adds a URL to the set associated with `term`.
     */
    public void add(String term, TermCounter tc) {
        //gets the redis key for the term that is passed in
        //also gets the String label (URL) for the TermCounter object that is passed in
        //adds to the set!
        jedis.sadd(urlSetKey(term), tc.getLabel());
    }
	
	/**
	 * Looks up a search term and returns a set of URLs.
	 * 
	 * @param term
	 * @return Set of URLs.
	 */
	public Set<String> getURLs(String term) {
        //urlSetKey gets the key for the term passed in
        //jedis.smembers returns the set of URLs for that key
        Set<String> URLset = jedis.smembers(urlSetKey(term));
		return URLset;
	}

    /**
	 * Looks up a term and returns a map from URL to count.
	 * 
	 * @param search term
	 * @return Map<String, Integer> from URL to count.
	 */
	public Map<String, Integer> getCounts(String term) {
        //get the URLs of the term passed in
        Set<String> URLs = getURLs(term); 
        //create the map to be returned
        Map<String, Integer> URLmap = new HashMap<String, Integer>();
        for(String url: URLs) {
            //for each URL in set
            //get the count of how many times term occurs
            Integer count = getCount(url, term);
            //System.out.println("URL: " + URL + " " + count);
            //place the URL and the resulting count number
            URLmap.put(url, count);
        }
        return URLmap;
    }

    /**
	 * Returns the number of times the given term appears at the given URL.
	 * 
	 * @param url
	 * @param term
	 * @return
	 */
	public Integer getCount(String url, String term) {
        //get the redis key for the url
        String rkey = termCounterKey(url);
        //get the count for the term
        Integer count = Integer.valueOf(jedis.hget(rkey, term));
        //System.out.println(count);
        //have to make an Integer object b/c count is stored as a String
        return count;
	}


	/**
	 * Add a web page to the index.
	 * 
	 * @param url         String URL of the page.
	 * @param paragraphs  Collection of JSoup elements that should be indexed.
	 */
	public void indexPage(String url, Elements paragraphs) {
        //create a new TermCounter 
        TermCounter terms = new TermCounter(url);
        //count the number of terms
        terms.processElements(paragraphs);

        
        //http://redis.io/commands/MULTI
        //multi marks the start of a transaction block
		Transaction trans = jedis.multi();
        //delete the key
		trans.del(termCounterKey(url));  
		
        //for each term, create a new entry in the termcounter
		for (String term: terms.keySet()) {
			trans.sadd(urlSetKey(term), url); 
            //also add a new member of the index
			trans.hset(termCounterKey(url), term, Integer.toString(terms.get(term)));  
		}
		
		trans.exec();
    }
    

	/**
	 * Prints the contents of the index.
	 * 
	 * Should be used for development and testing, not production.
	 */
	public void printIndex() {
		// loop through the search terms
		for (String term: termSet()) {
			System.out.println(term);
			
			// for each term, print the pages where it appears
			Set<String> urls = getURLs(term);
			for (String url: urls) {
				Integer count = getCount(url, term);
				System.out.println("    " + url + " " + count);
			}
		}
	}

	/**
	 * Returns the set of terms that have been indexed.
	 * 
	 * Should be used for development and testing, not production.
	 * 
	 * @return
	 */
	public Set<String> termSet() {
		Set<String> keys = urlSetKeys();
		Set<String> terms = new HashSet<String>();
		for (String key: keys) {
			String[] array = key.split(":");
			if (array.length < 2) {
				terms.add("");
			} else {
				terms.add(array[1]);
			}
		}
		return terms;
	}

	/**
	 * Returns URLSet keys for the terms that have been indexed.
	 * 
	 * Should be used for development and testing, not production.
	 * 
	 * @return
	 */
	public Set<String> urlSetKeys() {
		return jedis.keys("URLSet:*");
	}

	/**
	 * Returns TermCounter keys for the URLS that have been indexed.
	 * 
	 * Should be used for development and testing, not production.
	 * 
	 * @return
	 */
	public Set<String> termCounterKeys() {
		return jedis.keys("TermCounter:*");
	}

	/**
	 * Deletes all URLSet objects from the database.
	 * 
	 * Should be used for development and testing, not production.
	 * 
	 * @return
	 */
	public void deleteURLSets() {
		Set<String> keys = urlSetKeys();
		Transaction t = jedis.multi();
		for (String key: keys) {
			t.del(key);
		}
		t.exec();
	}

	/**
	 * Deletes all URLSet objects from the database.
	 * 
	 * Should be used for development and testing, not production.
	 * 
	 * @return
	 */
	public void deleteTermCounters() {
		Set<String> keys = termCounterKeys();
		Transaction t = jedis.multi();
		for (String key: keys) {
			t.del(key);
		}
		t.exec();
	}

	/**
	 * Deletes all keys from the database.
	 * 
	 * Should be used for development and testing, not production.
	 * 
	 * @return
	 */
	public void deleteAllKeys() {
		Set<String> keys = jedis.keys("*");
		Transaction t = jedis.multi();
		for (String key: keys) {
			t.del(key);
		}
		t.exec();
	}

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		Jedis jedis = JedisMaker.make();
		JedisIndex index = new JedisIndex(jedis);
		
		//index.deleteTermCounters();
		//index.deleteURLSets();
		//index.deleteAllKeys();
		//loadIndex(index);
		
		Map<String, Integer> map = index.getCounts("the");
		for (Entry<String, Integer> entry: map.entrySet()) {
			System.out.println(entry);
		}
	}

	/**
	 * Stores two pages in the index for testing purposes.
	 * 
	 * @return
	 * @throws IOException
	 */
	private static void loadIndex(JedisIndex index) throws IOException {
		WikiFetcher wf = new WikiFetcher();

		String url = "https://en.wikipedia.org/wiki/Java_(programming_language)";
		Elements paragraphs = wf.readWikipedia(url);
		index.indexPage(url, paragraphs);
		
		url = "https://en.wikipedia.org/wiki/Programming_language";
		paragraphs = wf.readWikipedia(url);
		index.indexPage(url, paragraphs);
	}
}
