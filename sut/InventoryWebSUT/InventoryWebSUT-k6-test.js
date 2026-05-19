import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    thresholds: {
        http_req_failed: ['rate<0.01'], 
        http_req_duration: ['p(95)<200'],
    },
    vus: 1, 
    duration: '10s',
};

// --- STEP INPUTS ---
// These will be populated by the Step "Inputs" table in your Keyword configuration
const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:30001/api';
const PRODUCT_NAME = __ENV.PRODUCT_NAME || 'k6 Performance Tool';
const PRODUCT_PRICE = parseFloat(__ENV.PRODUCT_PRICE || '150.00');
const SEARCH_TERM = __ENV.SEARCH_TERM || 'performance';

export default function () {
    const params = {
        headers: { 'Content-Type': 'application/json' },
    };

    // 1. RESET , cannot be executed with multiple K& vus in parallel
    //let resetRes = http.del(`${BASE_URL}/reset`);
    //check(resetRes, { 'reset successful': (r) => r.status === 204 });

    // 2. POST: Use variable for name and price
    let payload = JSON.stringify({ 
        name: PRODUCT_NAME, 
        price: PRODUCT_PRICE 
    });
    
    let postRes = http.post(`${BASE_URL}/products`, payload, params);
    let productId = postRes.json().id;
    
    check(postRes, {
        'post created 201': (r) => r.status === 201,
        'has product id': (r) => productId !== undefined,
    });

    // 2b. GET by ID: Retrieve the specific product created
    let getByIdRes = http.get(`${BASE_URL}/products/${productId}`);

    check(getByIdRes, {
        'get by id status 200': (r) => r.status === 200,
        'id matches': (r) => r.json().id === productId,
        'name matches': (r) => r.json().name === PRODUCT_NAME,
    });

    // 2c. GET by ID (Failure): Test 404 for a non-existent ID
    let getMissingRes = http.get(`${BASE_URL}/products/99999`);
    check(getMissingRes, {
        'non-existent id returns 404': (r) => r.status === 404,
    });

    // 3. GET: List all products
    let getRes = http.get(`${BASE_URL}/products`);
    check(getRes, {
        'list contains items': (r) => r.json().length > 0,
    });

    // 4. GET (Search): Use variable for search term
    let searchRes = http.get(`${BASE_URL}/products?name=${encodeURIComponent(SEARCH_TERM)}`);
    check(searchRes, {
        'search status 200': (r) => r.status === 200,
        'search found item': (r) => r.json().some(p => p.name.toLowerCase().includes(SEARCH_TERM.toLowerCase())),
    });

    // 5. PUT: Update the product (using the same variable name but adding a suffix)
    let updatePayload = JSON.stringify({ 
        name: `${PRODUCT_NAME} Updated`, 
        price: PRODUCT_PRICE + 50 
    });
    let putRes = http.put(`${BASE_URL}/products/${productId}`, updatePayload, params);
    check(putRes, { 'put updated 204': (r) => r.status === 204 });

    // 6. DELETE
    let delRes = http.del(`${BASE_URL}/products/${productId}`);
    check(delRes, { 'delete successful 204': (r) => r.status === 204 });

    sleep(1);
}