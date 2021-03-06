package com.kodcu.provider;

import com.kodcu.config.YamlConfiguration;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Aggregates.*;

/**
 * Created by Hakan on 5/18/2015.
 */
public class MongoToElasticProvider implements Provider {

    private final Logger logger = LoggerFactory.getLogger(MongoToElasticProvider.class);
    private final MongoCollection<Document> collection;
    private final YamlConfiguration config;
    private MongoCursor<Document> cursor;
    private long cursorId = 0;

    public MongoToElasticProvider(final MongoCollection<Document> collection, final YamlConfiguration config) {
        this.collection = collection;
        this.config = config;
    }

    @Override
    public long getCount() {
        long count = collection.count(Document.parse(config.getMongo().getQuery()));
        logger.info("Mongo collection count: " + count);
        if (count == 0) {
            logger.error("Database/Collection does not exist or does not contain the record");
            System.exit(-1);
        }
        return count;
    }

    @Override
    public List buildJSONContent(int skip, int limit) {
        ArrayList<Document> result = new ArrayList<>(limit);
        result.ensureCapacity(limit);

        MongoCursor<Document> cursor = getCursor(skip);
        while (cursor.hasNext() && result.size() < limit) {
            result.add(cursor.next());
        }
        return result;
    }

    /**
     * Get the MongoDB cursor.
     */
    private MongoCursor<Document> getCursor(int skip) {
        if (cursor == null && cursorId == 0) {
            Document query = Document.parse(config.getMongo().getQuery());
            List<Bson> pipes = new ArrayList<>(3);
            pipes.add(match(query));
            pipes.add(skip(skip));

            Optional.ofNullable(config.getMongo().getProject()).ifPresent(p -> pipes.add(project(Document.parse(p))));

            AggregateIterable<Document> aggregate = collection.aggregate(pipes)
                    .allowDiskUse(true)
                    .useCursor(true);

            cursor = aggregate.iterator();

            // TODO: Persist cursor ID somewhere to allow restarts.
            Optional.ofNullable(cursor.getServerCursor()).ifPresent(serverCursor -> cursorId = serverCursor.getId());
        } else if (cursor == null && cursorId != 0) {
            // TODO: Lookup cursor ID for resume.
            // Open existing cursor in case of restart??
        }

        return cursor;
    }
}
