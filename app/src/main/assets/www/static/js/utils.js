// static/js/utils.js

function haversine(lat1, lon1, lat2, lon2) {
    const R = 6371; // Radius of Earth in km
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
              Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
              Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
}

function compassdistance(c1, c2) {
    R = 6371; // Radius of Earth in km
    d = (c1 - c2) * Math.PI / 180;
    return Math.abs(R * d)
}

const countryToContinent = {
    "AF": "Asia", "AX": "Europe", "AL": "Europe", "DZ": "Africa", "AS": "Oceania", "AD": "Europe", "AO": "Africa", "AI": "North America", "AQ": "Antarctica", "AG": "North America", "AR": "South America", "AM": "Asia", "AW": "North America", "AU": "Oceania", "AT": "Europe", "AZ": "Asia", "BS": "North America", "BH": "Asia", "BD": "Asia", "BB": "North America", "BY": "Europe", "BE": "Europe", "BZ": "North America", "BJ": "Africa", "BM": "North America", "BT": "Asia", "BO": "South America", "BQ": "North America", "BA": "Europe", "BW": "Africa", "BR": "South America", "IO": "Asia", "VG": "North America", "BN": "Asia", "BG": "Europe", "BF": "Africa", "BI": "Africa", "KH": "Asia", "CM": "Africa", "CA": "North America", "CV": "Africa", "KY": "North America", "CF": "Africa", "TD": "Africa", "CL": "South America", "CN": "Asia", "CX": "Asia", "CC": "Asia", "CO": "South America", "KM": "Africa", "CK": "Oceania", "CR": "North America", "HR": "Europe", "CU": "North America", "CW": "North America", "CY": "Asia", "CZ": "Europe", "CD": "Africa", "DK": "Europe", "DJ": "Africa", "DM": "North America", "DO": "North America", "EC": "South America", "EG": "Africa", "SV": "North America", "GQ": "Africa", "ER": "Africa", "EE": "Europe", "ET": "Africa", "FK": "South America", "FO": "Europe", "FJ": "Oceania", "FI": "Europe", "FR": "Europe", "GF": "South America", "PF": "Oceania", "GA": "Africa", "GM": "Africa", "GE": "Asia", "DE": "Europe", "GH": "Africa", "GI": "Europe", "GR": "Europe", "GL": "North America", "GD": "North America", "GP": "North America", "GU": "Oceania", "GT": "North America", "GG": "Europe", "GN": "Africa", "GW": "Africa", "GY": "South America", "HT": "North America", "HN": "North America", "HK": "Asia", "HU": "Europe", "IS": "Europe", "IN": "Asia", "ID": "Asia", "IR": "Asia", "IQ": "Asia", "IE": "Europe", "IM": "Europe", "IL": "Asia", "IT": "Europe", "CI": "Africa", "JM": "North America", "JP": "Asia", "JE": "Europe", "JO": "Asia", "KZ": "Asia", "KE": "Africa", "KI": "Oceania", "KW": "Asia", "KG": "Asia", "LA": "Asia", "LV": "Europe", "LB": "Asia", "LS": "Africa", "LR": "Africa", "LY": "Africa", "LI": "Europe", "LT": "Europe", "LU": "Europe", "MO": "Asia", "MK": "Europe", "MG": "Africa", "MW": "Africa", "MY": "Asia", "MV": "Asia", "ML": "Africa", "MT": "Europe", "MH": "Oceania", "MQ": "North America", "MR": "Africa", "MU": "Africa", "YT": "Africa", "MX": "North America", "FM": "Oceania", "MD": "Europe", "MC": "Europe", "MN": "Asia", "ME": "Europe", "MS": "North America", "MA": "Africa", "MZ": "Africa", "MM": "Asia", "NA": "Africa", "NR": "Oceania", "NP": "Asia", "NL": "Europe", "NC": "Oceania", "NZ": "Oceania", "NI": "North America", "NE": "Africa", "NG": "Africa", "NU": "Oceania", "NF": "Oceania", "KP": "Asia", "MP": "Oceania", "NO": "Europe", "OM": "Asia", "PK": "Asia", "PW": "Oceania", "PS": "Asia", "PA": "North America", "PG": "Oceania", "PY": "South America", "PE": "South America", "PH": "Asia", "PN": "Oceania", "PL": "Europe", "PT": "Europe", "PR": "North America", "QA": "Asia", "CG": "Africa", "RO": "Europe", "RU": "Europe", "RW": "Africa", "RE": "Africa", "BL": "North America", "SH": "Africa", "KN": "North America", "LC": "North America", "MF": "North America", "PM": "North America", "VC": "North America", "WS": "Oceania", "SM": "Europe", "ST": "Africa", "SA": "Asia", "SN": "Africa", "RS": "Europe", "SC": "Africa", "SL": "Africa", "SG": "Asia", "SX": "North America", "SK": "Europe", "SI": "Europe", "SB": "Oceania", "SO": "Africa", "ZA": "Africa", "GS": "Antarctica", "KR": "Asia", "SS": "Africa", "ES": "Europe", "LK": "Asia", "SD": "Africa", "SR": "South America", "SJ": "Europe", "SZ": "Africa", "SE": "Europe", "CH": "Europe", "SY": "Asia", "TW": "Asia", "TJ": "Asia", "TZ": "Africa", "TH": "Asia", "TL": "Asia", "TG": "Africa", "TK": "Oceania", "TO": "Oceania", "TT": "North America", "TN": "Africa", "TR": "Asia", "TM": "Asia", "TC": "North America", "TV": "Oceania", "UG": "Africa", "UA": "Europe", "AE": "Asia", "GB": "Europe", "US": "North America", "UM": "Oceania", "VI": "North America", "UY": "South America", "UZ": "Asia", "VU": "Oceania", "VA": "Europe", "VE": "South America", "VN": "Asia", "WF": "Oceania", "EH": "Africa", "YE": "Asia", "ZM": "Africa", "ZW": "Africa"
};

/**
 * Calculates a series of points along a great-circle arc for drawing curved lines.
 */
function getGreatCirclePoints(start, end) {
    const points = [];
    const numPoints = 100;
    const lat1 = start.lat * Math.PI / 180, lon1 = start.lng * Math.PI / 180;
    const lat2 = end.lat * Math.PI / 180, lon2 = end.lng * Math.PI / 180;
    const d = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin((lat1 - lat2) / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin((lon1 - lon2) / 2), 2)));
    for (let i = 0; i <= numPoints; i++) {
        const f = i / numPoints;
        if (Math.sin(d) === 0) { points.push([start.lat, start.lng]); continue; }
        const A = Math.sin((1 - f) * d) / Math.sin(d);
        const B = Math.sin(f * d) / Math.sin(d);
        const x = A * Math.cos(lat1) * Math.cos(lon1) + B * Math.cos(lat2) * Math.cos(lon2);
        const y = A * Math.cos(lat1) * Math.sin(lon1) + B * Math.cos(lat2) * Math.sin(lon2);
        const z = A * Math.sin(lat1) + B * Math.sin(lat2);
        const lat = Math.atan2(z, Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2))) * 180 / Math.PI;
        const lon = Math.atan2(y, x) * 180 / Math.PI;
        points.push([lat, lon]);
    }
    return points;
}

