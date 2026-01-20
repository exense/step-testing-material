using Microsoft.AspNetCore.Mvc;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Collections.Concurrent;
using Microsoft.Extensions.FileProviders;
using System.Reflection;

var builder = WebApplication.CreateBuilder(args);

// Ensure JSON Source Gen knows about our types
builder.Services.ConfigureHttpJsonOptions(options =>
{
    options.SerializerOptions.TypeInfoResolverChain.Insert(0, SourceGenerationContext.Default);
});

builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

var app = builder.Build();

// 1. Tell .NET to look INSIDE the binary for files in prod mode or local resource for dev run
IFileProvider fileProvider;

if (app.Environment.IsDevelopment())
{
    // Use the physical path of the source code during development
    var pathToWebRoot = Path.Combine(builder.Environment.ContentRootPath, "wwwroot");
    fileProvider = new PhysicalFileProvider(pathToWebRoot);
}
else
{
    // Use the resources inside the binary for the Pod
    // Note: Use the exact Assembly Name as the prefix (e.g., InventoryWebSUT)
    var assembly = Assembly.GetExecutingAssembly();
    fileProvider = new EmbeddedFileProvider(assembly, $"{assembly.GetName().Name}.wwwroot");
}

app.UseDefaultFiles(new DefaultFilesOptions { FileProvider = fileProvider });
app.UseStaticFiles(new StaticFileOptions { FileProvider = fileProvider });

app.UseSwagger();
app.UseSwaggerUI(c => {
    c.SwaggerEndpoint("/swagger/v1/swagger.json", "SUT API V1");
    c.RoutePrefix = "swagger"; // Swagger now at /swagger/index.html
});

// --- STATE MANAGEMENT ---
const int MAX_PRODUCTS = 1000;
// Atomic counter for ID generation
int _idCounter = 2; 
// Initialize the Map directly using the Product record
var products = new ConcurrentDictionary<int, Product>();
products.TryAdd(1, new Product { Id = 1, Name = "Automation Tool", Price = 99.99 });
products.TryAdd(2, new Product { Id = 2, Name = "Performance Script", Price = 49.50 });

// --- ENDPOINTS ---

app.MapGet("/api/products", (string? name) => 
    string.IsNullOrEmpty(name) 
        ? products.Values 
        : products.Values.Where(p => p.Name.Contains(name, StringComparison.OrdinalIgnoreCase)));

app.MapPost("/api/products", ([FromBody] Product newProduct) => 
{
    if (products.Count >= MAX_PRODUCTS)
        return Results.BadRequest("Memory limit reached.");

    // ID Logic: Generate if 0/null, check if exists otherwise
    int finalId = newProduct.Id;
    if (finalId <= 0)
    {
        finalId = Interlocked.Increment(ref _idCounter);
    }
    else if (products.ContainsKey(finalId))
    {
        return Results.BadRequest($"Product with ID {finalId} already exists.");
    }

    var productToAdd = newProduct with { Id = finalId };
    products.TryAdd(finalId, productToAdd);
    
    return Results.Created($"/api/products/{finalId}", productToAdd);
});

app.MapPut("/api/products/{id}", (int id, [FromBody] Product updatedProduct) => 
{
    if (!products.ContainsKey(id)) return Results.NotFound();
    
    products[id] = updatedProduct with { Id = id };
    return Results.NoContent();
});

app.MapDelete("/api/products/{id}", (int id) => 
    products.TryRemove(id, out _) ? Results.NoContent() : Results.NotFound());

// --- RESET SERVICE ---
// Clears all products and resets the ID counter to zero
app.MapDelete("/api/reset", () => 
{
    products.Clear();
    
    // Atomically reset the counter to 0 (or 2 if you want to re-seed)
    Interlocked.Exchange(ref _idCounter, 0); 
    
    return Results.NoContent();
});

app.Run();

// --- MODELS ---
// Using explicit properties ensures Swagger shows Name and Price in the UI
public record Product
{
    public int Id { get; init; }
    public string Name { get; init; } = string.Empty;
    public double Price { get; init; }
}

[JsonSerializable(typeof(Product))]
[JsonSerializable(typeof(List<Product>))]
[JsonSerializable(typeof(IEnumerable<Product>))]
[JsonSerializable(typeof(JsonElement))]
internal partial class SourceGenerationContext : JsonSerializerContext { }