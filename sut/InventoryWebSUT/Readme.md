# InventoryWebSUT

A high-performance, self-contained .NET 8 "System Under Test" (SUT) designed for both UI (Selenium/Playwright) and API (k6/JMeter) automation testing.

## Features
- **Zero Runtime Dependency**: Compiles to a single native binary (approx. 60MB).
- **Embedded UI**: A built-in management dashboard (Alpine.js) baked into the binary.
- **Embedded Swagger UI**: A built-in swagger UI to interract with the REST API.
- **In-Memory Store**: Uses a thread-safe `ConcurrentDictionary` with a 1,000-item safety limit.
- **Automation Ready**: Includes a `/api/reset` endpoint and `data-testid` attributes for reliable testing.

## Local run

1. Simply use the run (F5) capabilities of Visual Studio Code or execute
```bash
dotnet run
```
2. Access the UI: Open http://127.0.01:30001

3. Access API Docs: Open http://127.0.01:30001/swagger

## Build Instructions
To generate the optimized single-file binary:
```bash
   dotnet publish -c Release -r linux-x64 --self-contained true -p:PublishSingleFile=true -p:PublishTrimmed=true
```

The released binary can be found under `bin\Release\net8.0\linux-x64\publish\InventoryWebSUT`

## Quick Start (WSL/Linux)
1. **Run the binary**:
   ```bash
   ./InventoryWebSUT --urls "http://0.0.0.0:30001"
   ```
   
2. Access the UI: Open http://127.0.01:30001

3. Access API Docs: Open http://127.0.01:30001/swagger

## API Summary

|Method|Endpoint|Description|
|--|--|--|
|GET|/api/productsList| all items (supports ?name= filter)POST/api/productsCreate an item (Auto-generates ID if 0)|
|PUT|/api/products/{id}|Update an existing item|
|DELETE|/api/products/{id}|Remove an item|
|DELETE|/api/reset|Wipe all data and reset ID counter|