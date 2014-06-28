package fr.ydelouis.selfoss.model;

import com.j256.ormlite.dao.BaseDaoImpl;
import com.j256.ormlite.support.ConnectionSource;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import fr.ydelouis.selfoss.entity.Tag;

public class TagDao extends BaseDaoImpl<Tag, String> {

    public static final String COLUMN_NAME = "name";

    public TagDao(ConnectionSource connectionSource) throws SQLException {
        super(connectionSource, Tag.class);
    }

    public List<Tag> queryForNames(List<String> tagsNames) {
        try {
            return queryBuilder().where().in(COLUMN_NAME, tagsNames).query();
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<Tag>();
        }
    }
}
