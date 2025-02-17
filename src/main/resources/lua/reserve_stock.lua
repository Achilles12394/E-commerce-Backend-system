local available = redis.call('GET', KEYS[1])
if (not available) then
    return -1
end

available = tonumber(available)
local quantity = tonumber(ARGV[1])

if (available < quantity) then
    return 0
end

redis.call('DECRBY', KEYS[1], quantity)
redis.call('HINCRBY', KEYS[2], ARGV[2], quantity)
return 1
