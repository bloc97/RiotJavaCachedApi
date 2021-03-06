/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.bloc97.riot.cache.database;

import java.util.Collections;
import java.util.Comparator;
import net.bloc97.riot.cache.cached.GenericObjectCache;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static net.bloc97.riot.cache.CachedRiotApi.isRateLimited;
import net.rithms.riot.api.RiotApi;
import net.rithms.riot.api.RiotApiException;
import net.rithms.riot.api.endpoints.champion_mastery.dto.ChampionMastery;
import net.rithms.riot.constant.Platform;

/**
 *
 * @author bowen
 */
public class ChampionMasteryDatabase implements CachedDatabase {
    private static final long LIFE = TimeUnit.MINUTES.toMillis(20); //Caching Time to live
    public final int version = 3;
    
    private final RiotApi rApi;
    private final Platform platform;
    
    private final Map<Long, GenericObjectCache<List<ChampionMastery>>> championMasteriesCache; //Maps summoner ID to ChampionMastery
    private final Map<Long, GenericObjectCache<Integer>> scoresCache; //Maps summoner ID to score
    
    public ChampionMasteryDatabase(Platform platform, RiotApi rApi) {
        this.rApi = rApi;
        this.platform = platform;
        
        championMasteriesCache = new HashMap<>();
        scoresCache = new HashMap<>();
    }
    
    //Updaters, calls RiotApi for cache updates
    private List<ChampionMastery> updateChampionMasteriesBySummoner(long id, Date now) {
        List<ChampionMastery> data = null;
        try {
            data = rApi.getChampionMasteriesBySummoner(platform, id);
        } catch (RiotApiException ex) {
            if (isRateLimited(ex)) {
                return updateChampionMasteriesBySummoner(id, now);
            }
            System.out.println(ex);
            championMasteriesCache.remove(id);
        }
        if (data != null) {
            championMasteriesCache.put(id, new GenericObjectCache(data, now, LIFE));
        }
        return data;
    }
    private int updateChampionMasteryScoresBySummoner(long id, Date now) {
        Integer data = null;
        try {
            data = rApi.getChampionMasteryScoresBySummoner(platform, id);
        } catch (RiotApiException ex) {
            if (isRateLimited(ex)) {
                return updateChampionMasteryScoresBySummoner(id, now);
            }
            System.out.println(ex);
            championMasteriesCache.remove(id);
        }
        if (data != null) {
            championMasteriesCache.put(id, new GenericObjectCache(data, now, LIFE));
        }
        return data;
    }
    
    
    
    public List<ChampionMastery> getChampionMasteriesBySummoner(long id) {
        Date now = new Date();
        
        GenericObjectCache<List<ChampionMastery>> cache = championMasteriesCache.get(id);
        if (cache == null) {
            return updateChampionMasteriesBySummoner(id, now);
        }
        if (cache.isValid(now)) {
            return cache.getObject();
        } else {
            return updateChampionMasteriesBySummoner(id, now);
        }
    }
    
    
    public ChampionMastery getChampionMasteryBySummonerByChampion(long id, int championId) {
        List<ChampionMastery> championMasteries = getChampionMasteriesBySummoner(id);
        
        for (ChampionMastery cm : championMasteries) {
            if (cm.getChampionId() == championId) {
                return cm;
            }
        }
        return null;
    }
    public int getChampionMasteryScoreBySummoner(long id) {
        Date now = new Date();
        
        GenericObjectCache<Integer> cache = scoresCache.get(id);
        if (cache == null) {
            return updateChampionMasteryScoresBySummoner(id, now);
        }
        if (cache.isValid(now)) {
            return cache.getObject();
        } else {
            return updateChampionMasteryScoresBySummoner(id, now);
        }
    }

    //Extra functions
    public enum CompareMethod {
        ID, LEVEL, POINTS, LASTPLAYED, TOKENSEARNED;
    }
    
    public void sortChampionMasteries(List<ChampionMastery> list, CompareMethod method, boolean isAscending) {
        Comparator<ChampionMastery> comparator = null;
        
        switch (method) {
            case ID:
                comparator = (ChampionMastery o1, ChampionMastery o2) -> o1.getChampionId() - o2.getChampionId();
                break;
            case LEVEL:
                comparator = (ChampionMastery o1, ChampionMastery o2) -> (o1.getChampionLevel() == o2.getChampionLevel()) ? (int)(o1.getChampionPoints() - o2.getChampionPoints()) : (o1.getChampionLevel() - o2.getChampionLevel());
                break;
            case POINTS:
                //Assuming it is already sorted by points
                //comparator = (ChampionMastery o1, ChampionMastery o2) -> (int)(o1.getChampionPoints( )- o2.getChampionPoints());
                Collections.reverse(list);
                break;
            case LASTPLAYED:
                comparator = (ChampionMastery o1, ChampionMastery o2) -> (int)(o1.getLastPlayTime() - o2.getLastPlayTime());
                break;
            case TOKENSEARNED:
                comparator = (ChampionMastery o1, ChampionMastery o2) -> o1.getTokensEarned() - o2.getTokensEarned();
                break;
            default:
        }
        
        if (comparator != null) {
            Collections.sort(list, comparator);
        }
        if (!isAscending) Collections.reverse(list);
    }
    
    @Override
    public void purge() {
        championMasteriesCache.clear();
        scoresCache.clear();
    }
    
}
