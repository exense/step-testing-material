exports.NodeJS_Echo = async (input, output) => {
  output.send({ echo: input.message });
};
