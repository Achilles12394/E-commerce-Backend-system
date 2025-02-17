local activityStatus = redis.call('HGET', KEYS[1], 'status')
local startAt = redis.call('HGET', KEYS[1], 'startAt')
local endAt = redis.call('HGET', KEYS[1], 'endAt')

if (not activityStatus or not startAt or not endAt) then
    return -1
end

local nowMillis = tonumber(ARGV[1])
if (activityStatus ~= '1' or nowMillis < tonumber(startAt) or nowMillis > tonumber(endAt)) then
    return -2
end

local stock = redis.call('GET', KEYS[2])
if (not stock) then
    return -1
end

stock = tonumber(stock)
local quantity = tonumber(ARGV[2])
if (stock < quantity) then
    return 0
end

local limitPerUser = tonumber(ARGV[3])
local bought = redis.call('GET', KEYS[3])
if (not bought) then
    bought = 0
else
    bought = tonumber(bought)
end

if (bought + quantity > limitPerUser) then
    return -3
end

redis.call('DECRBY', KEYS[2], quantity)
redis.call('INCRBY', KEYS[3], quantity)
return 1
