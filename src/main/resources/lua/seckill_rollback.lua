local quantity = tonumber(ARGV[1])
redis.call('INCRBY', KEYS[1], quantity)

local bought = redis.call('GET', KEYS[2])
if (not bought) then
    return 0
end

bought = tonumber(bought) - quantity
if (bought <= 0) then
    redis.call('DEL', KEYS[2])
else
    redis.call('SET', KEYS[2], bought)
end

return 1
