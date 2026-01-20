import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    thresholds: {
        http_req_failed: ['rate<0.01'], // Fail test if more than 1% of requests fail
        http_req_duration: ['p(95)<200'], // 95% of requests should be under 200ms
    },
    vus: 1, // Single user for sequence testing
    duration: '10s',
};

const BASE_URL = 'http://127.0.0.1:30001/api'; // Update with your SUT IP if needed

export default function () {
    const params = {
        headers: { 'Content-Type': 'application/json' },
    };

    // 1. RESET: Start with a clean slate
    let resetRes = http.del(`${BASE_URL}/reset`);
    check(resetRes, { 'reset successful': (r) => r.status === 204 });

    // 2. POST: Create a new product
    let payload = JSON.stringify({ name: 'k6 Performance Tool', price: 150.00 });
    let postRes = http.post(`${BASE_URL}/products`, payload, params);
    let productId = postRes.json().id;
    
    check(postRes, {
        'post created 201': (r) => r.status === 201,
        'has product id': (r) => productId !== undefined,
    });

    // 3. GET: List all products
    let getRes = http.get(`${BASE_URL}/products`);
    check(getRes, {
        'list contains items': (r) => r.json().length > 0,
    });

    // 4. GET (Search): Filter by name
    let searchRes = http.get(`${BASE_URL}/products?name=performance`);
    check(searchRes, {
        'search found item': (r) => r.json().length === 1 && r.json()[0].name.toLowerCase().includes('performance'),
    });

    // 5. PUT: Update the product
    let updatePayload = JSON.stringify({ name: 'k6 PRO', price: 200.00 });
    let putRes = http.put(`${BASE_URL}/products/${productId}`, updatePayload, params);
    check(putRes, { 'put updated 204': (r) => r.status === 204 });

    // 6. DELETE: Remove the product
    let delRes = http.del(`${BASE_URL}/products/${productId}`);
    check(delRes, { 'delete successful 204': (r) => r.status === 204 });

    sleep(1); // Wait 1 second between iterations
}