// static/js/database.js

const DB_NAME = 'FlightStatsDB';
const STORE_NAME = 'flights';

async function initDB() {
    const db = await idb.openDB(DB_NAME, 1, {
        upgrade(db) {
            if (!db.objectStoreNames.contains(STORE_NAME)) {
                db.createObjectStore(STORE_NAME, { keyPath: 'id', autoIncrement: true });
            }
        },
    });
    return db;
}

async function addFlight(flight) {
    const db = await initDB();
    return db.add(STORE_NAME, flight);
}

async function getAllFlights() {
    const db = await initDB();
    const allFlights = await db.getAll(STORE_NAME);
    // Sort by date descending in JavaScript
    allFlights.sort((a, b) => new Date(b.date) - new Date(a.date));
    return allFlights;
}

async function getFlight(id) {
    const db = await initDB();
    return db.get(STORE_NAME, id);
}

async function updateFlight(flight) {
    const db = await initDB();
    return db.put(STORE_NAME, flight);
}

async function deleteFlight(id) {
    const db = await initDB();
    return db.delete(STORE_NAME, id);
}

async function importFlights(flightArray) {
    const db = await initDB();
    const tx = db.transaction(STORE_NAME, 'readwrite');
    const promises = flightArray.map(flight => tx.store.add(flight));
    await Promise.all(promises);
    return tx.done;
}