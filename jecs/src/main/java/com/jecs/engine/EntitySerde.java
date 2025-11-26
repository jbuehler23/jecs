package com.jecs.engine;

import com.google.gson.*;

import java.lang.reflect.Type;

public class EntitySerde implements JsonDeserializer<Entity> {

    @Override
    public Entity deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        String entityName = jsonObject.get("name").getAsString();
        JsonArray components = jsonObject.getAsJsonArray("components");
        Transform transform = jsonDeserializationContext.deserialize(jsonObject.get("transform"), Transform.class);
        int zIndex = jsonDeserializationContext.deserialize(jsonObject.get("zIndex"), int.class);

        Entity entity = new Entity(entityName, transform, zIndex);
        for (JsonElement e : components) {
            Component c = jsonDeserializationContext.deserialize(e, Component.class);
            entity.addComponent(c);
        }
        return entity;
    }
}
